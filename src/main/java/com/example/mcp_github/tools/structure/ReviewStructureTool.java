package com.example.mcp_github.tools.structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_github.service.ArchitecturePromptService;
import com.example.mcp_github.service.DocumentationWriterService;
import com.example.mcp_github.service.DocumentationWriterService.WriteResult;
import com.example.mcp_github.service.GitHubFileTreeService;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.MemoryService;
import com.example.mcp_github.service.ProjectContextService;
import com.example.mcp_github.service.ProjectContextService.GitProjectContext;
import com.example.mcp_github.service.ProjectStructureAnalyzer;
import com.example.mcp_github.service.ProjectStructureAnalyzer.AnalysisResult;
import com.example.mcp_github.service.RolloDocsBuilder;
import com.example.mcp_github.service.DocumentationContextBuilder;
import com.example.mcp_github.model.ProjectFingerprint;

/**
 * ReviewStructureTool — Validation & Refinement Step
 *
 * Called AFTER analyzeProjectStructure has produced rollo.md or
 * rollo_detailled.md. This tool re-reads the generated file, cross-checks it
 * against a fresh static analysis, and asks the LLM to critique its own output
 * from the perspective of a Senior Architecture Reviewer.
 *
 * Outcomes: - If the analysis is accurate → returns a ✅ validation
 * confirmation, no file written. - If issues are found → writes
 * rollo_refined.md with corrected findings.
 */
@Component
public class ReviewStructureTool {

    private static final Logger logger = LoggerFactory.getLogger(ReviewStructureTool.class);

    private final GitHubFileTreeService gitHubFileTreeService;
    private final ProjectStructureAnalyzer analyzer;
    private final MemoryService memoryService;
    private final ProjectContextService projectContextService;
    private final ArchitecturePromptService promptService;
    private final DocumentationWriterService writerService;
    private final RolloDocsBuilder rolloDocsBuilder;
    private final DocumentationContextBuilder contextBuilder;

    public ReviewStructureTool(
            GitHubFileTreeService gitHubFileTreeService,
            ProjectStructureAnalyzer analyzer,
            MemoryService memoryService,
            ProjectContextService projectContextService,
            ArchitecturePromptService promptService,
            DocumentationWriterService writerService,
            RolloDocsBuilder rolloDocsBuilder,
            DocumentationContextBuilder contextBuilder) {
        this.gitHubFileTreeService = gitHubFileTreeService;
        this.analyzer = analyzer;
        this.memoryService = memoryService;
        this.projectContextService = projectContextService;
        this.promptService = promptService;
        this.writerService = writerService;
        this.rolloDocsBuilder = rolloDocsBuilder;
        this.contextBuilder = contextBuilder;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOOL DEFINITION
    // ═══════════════════════════════════════════════════════════════════════════
    @Tool(name = "reviewProjectStructure", description = """
            Call this tool AFTER analyzeProjectStructure has run and produced rollo.md
            or rollo_detailled.md.

            This tool acts as a validation step: it re-reads the generated documentation,
            re-runs a fresh static analysis on the repository, and produces a critique
            of the previous findings from the perspective of a Senior Architecture Reviewer.

            Outcomes:
            - If the analysis is accurate and complete → confirms validation. No new file written.
            - If corrections or additions are needed → writes rollo_refined.md with an
              improved architectural assessment.

            Use this tool when:
            - The user asks to "validate", "review", "check", "refine" the architecture doc.
            - After any analyzeProjectStructure call when the user wants higher confidence.
            - When the confidence score from the first analysis was below 70%.

            The repository is resolved from memory if not specified explicitly.
            """)
    public String reviewProjectStructure(
            @ToolParam(description = "GitHub repository 'owner/repo'. Omit if already in memory.", required = false) String repository,
            @ToolParam(description = "Branch name (default: from memory or 'main')", required = false) String branch,
            @ToolParam(description = "Which file to review: 'standard' (rollo.md) or 'detailed' (rollo_detailled.md). Default: standard.", required = false) String targetFile) {

        logger.info("TOOL: reviewProjectStructure — repository={} branch={} targetFile={}", repository, branch,
                targetFile);

        try {
            // ── 1. Resolve project target ─────────────────────────────────────
            ResolvedTarget target = resolveTarget(repository, branch);
            if (target.error() != null) {
                return target.error();
            }

            // ── 2. Determine which file to review ─────────────────────────────
            boolean reviewDetailed = "detailed".equalsIgnoreCase(targetFile);
            String fileName = reviewDetailed ? "rollo_detailled.md" : "rollo.md";

            // ── 3. Read the existing documentation file ───────────────────────
            String existingDoc = readExistingDoc(target.path(), fileName);
            if (existingDoc == null) {
                return ("⚠️ Fichier `%s` introuvable dans `%s`.\n"
                        + "Lancez d'abord `analyzeProjectStructure` pour générer la documentation.")
                        .formatted(fileName, target.path());
            }

            // ── 4. Re-run fresh static analysis ───────────────────────────────
            RepositorySnapshot snapshot = gitHubFileTreeService.snapshot(
                    target.owner(), target.repo(), target.branch());

            if (snapshot.tree().isEmpty()) {
                return "ERREUR : Impossible de récupérer l'arborescence de `%s/%s` [%s]."
                        .formatted(target.owner(), target.repo(), target.branch());
            }

            AnalysisResult freshAnalysis = analyzer.analyze(snapshot);
            ProjectFingerprint fingerprint = rolloDocsBuilder.build(target.path(), target.owner(), target.repo(), target.branch(), snapshot, freshAnalysis);

            // ── 5. Build the critic prompt ────────────────────────────────────
            String criticPrompt = buildCriticPrompt(existingDoc, fingerprint, snapshot, fileName);

            // ── 6. Return prompt to AI for self-critique ──────────────────────
            //
            // In the MCP/agentic flow the AI itself will process this prompt and
            // decide whether to call writeRefinedDoc() or confirm validation.
            // We surface both the prompt AND the fresh analysis data so the AI
            // has everything it needs to produce its verdict.
            //
            return buildReviewResponse(criticPrompt, fingerprint, snapshot, target, fileName);

        } catch (Exception e) {
            logger.error("reviewProjectStructure a échoué", e);
            return "ERREUR : " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Second tool: called by the AI once it has produced its critique verdict
    // ─────────────────────────────────────────────────────────────────────────
    @Tool(name = "writeRefinedDoc", description = """
            Call this tool ONLY after reviewProjectStructure has run AND you have
            determined that the original documentation contains inaccuracies or
            significant omissions that warrant a corrected version.

            Do NOT call this tool if the original analysis was accurate — in that
            case simply confirm validation in chat.

            Parameters:
            - refinedContent : the full corrected Markdown body you have produced
              as the Senior Architecture Reviewer.
            - reviewSummary  : a short summary (3-5 bullet points) of what was
              changed and why.
            - repository / branch : resolved from memory if omitted.
            """)
    public String writeRefinedDoc(
            @ToolParam(description = "The full corrected Markdown content for rollo_refined.md") String refinedContent,
            @ToolParam(description = "Short summary of corrections made (3-5 bullet points)") String reviewSummary,
            @ToolParam(description = "GitHub repository 'owner/repo'. Omit if already in memory.", required = false) String repository,
            @ToolParam(description = "Branch name (default: from memory or 'main')", required = false) String branch) {

        logger.info("TOOL: writeRefinedDoc — repository={} branch={}", repository, branch);

        try {
            ResolvedTarget target = resolveTarget(repository, branch);
            if (target.error() != null) {
                return target.error();
            }

            if (refinedContent == null || refinedContent.isBlank()) {
                return "ERREUR : refinedContent ne peut pas être vide.";
            }

            // Build the review header that will precede the refined body
            String reviewHeader = buildReviewHeader(reviewSummary, target);

            // Write rollo_refined.md via the writer service
            WriteResult result = writerService.writeRefined(
                    target.path(), reviewHeader, refinedContent,
                    target.owner(), target.repo(), target.branch());

            if (result instanceof WriteResult.Success s) {
                return ("✅ `rollo_refined.md` généré (%d octets) → `%s`\n\n"
                        + "**Corrections apportées :**\n%s")
                        .formatted(s.bytes(), s.path().toAbsolutePath(), reviewSummary);
            } else if (result instanceof WriteResult.Skipped sk) {
                return writerService.formatWarningMessage(sk.reason());
            } else if (result instanceof WriteResult.Failed f) {
                return writerService.formatWarningMessage(f.reason());
            }

            return "ERREUR : Résultat inattendu de writeRefined.";

        } catch (Exception e) {
            logger.error("writeRefinedDoc a échoué", e);
            return "ERREUR : " + e.getMessage();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRITIC PROMPT — zero-shot Senior Reviewer role
    // ═══════════════════════════════════════════════════════════════════════════
    /**
     * Builds the self-critique prompt. The LLM is placed in the role of a
     * Senior Architecture Reviewer whose job is to find flaws — not describe
     * the project again. This avoids the model simply paraphrasing itself.
     */
    private String buildCriticPrompt(String existingDoc, ProjectFingerprint fingerprint,
            RepositorySnapshot snapshot, String fileName) {

        return """
                ## REVIEW TASK — Senior Architecture Reviewer

                You previously produced the architectural documentation below (`%s`).
                A fresh static analysis of the same repository has now been run.
                Your task is to CRITICALLY REVIEW your own output — not describe it again.

                ### Instructions
                - Act as a Senior Architecture Reviewer, NOT as the original author.
                - Your goal is to find errors, omissions, overclaims, or misclassifications.
                - Focus specifically on advanced engineering practices relevant to the detected stack (e.g., data transfer isolation, security layers, SOLID principles, and clean separation of concerns).
                - Focus on: pattern classification accuracy, layer boundary correctness,
                  missing components, incorrect signals, overstated confidence.
                - Be concise and specific. Reference line numbers or sections when possible.
                - Do NOT repeat correct findings — only flag what needs correction.

                ### Fresh Static Analysis (ground truth)
%s

                ### Original Documentation to Review
                ---
                %s
                ---

                ### Your Verdict
                Answer one of:
                A) ✅ VALIDATED — The analysis is accurate and complete. No refinement needed.
                B) ⚠️ REFINEMENT NEEDED — List specific issues found, then call `writeRefinedDoc`
                   with the corrected full document.

                Be strict. A confidence delta > 15%% or a wrong pattern classification
                always requires refinement.
                """.formatted(
                fileName,
                contextBuilder.buildPromptContext(fingerprint),
                existingDoc);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REVIEW RESPONSE — what the tool returns to the AI
    // ═══════════════════════════════════════════════════════════════════════════
    private String buildReviewResponse(String criticPrompt, ProjectFingerprint fingerprint,
            RepositorySnapshot snapshot, ResolvedTarget target, String fileName) {

        StringBuilder r = new StringBuilder();

        r.append("**Review initiated** — `%s/%s` · `%s` · reviewing `%s`\n\n"
                .formatted(target.owner(), target.repo(), target.branch(), fileName));

        // Surface the delta between original doc signals and fresh analysis
        r.append("**Fresh analysis snapshot:**\n");
        r.append("- Pattern: `%s` · Confidence: `%d%%`\n"
                .formatted(fingerprint.primaryPattern().name(), fingerprint.patternConfidence()));
        r.append("- Files scanned: `%d`\n".formatted(snapshot.tree().size()));
        if (!fingerprint.keySignals().isEmpty()) {
            r.append("- Signals: %s\n".formatted(String.join(", ", fingerprint.keySignals())));
        }
        r.append("\n---\n\n");

        // The critic prompt is what the AI must now process
        r.append(criticPrompt);

        return r.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REFINED DOC HEADER
    // ═══════════════════════════════════════════════════════════════════════════
    private String buildReviewHeader(String reviewSummary, ResolvedTarget target) {
        String now = LocalDate.now().toString();
        return """
                ## Revue & Raffinement Architectural

                | Champ | Valeur |
                |-------|--------|
                | Dépôt | `%s/%s` |
                | Branche | `%s` |
                | Généré le | `%s` |
                | Type | Raffinement post-analyse |

                ### Corrections Apportées

                %s

                ---

                """.formatted(target.owner(), target.repo(), target.branch(), now, reviewSummary);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE READING
    // ═══════════════════════════════════════════════════════════════════════════
    /**
     * Reads the existing rollo.md or rollo_detailled.md from disk. Returns null
     * if the file does not exist or cannot be read.
     */
    private String readExistingDoc(String projectPath, String fileName) {
        if (projectPath == null || projectPath.isBlank() || projectPath.equals("manual")) {
            return null;
        }
        try {
            Path filePath = Paths.get(projectPath).resolve(fileName);
            if (!Files.exists(filePath)) {
                return null;
            }
            String content = Files.readString(filePath);
            // Truncate very large files to avoid token overflow in the critic prompt
            if (content.length() > 12_000) {
                return content.substring(0, 12_000) + "\n\n[... truncated for review ...]";
            }
            return content;
        } catch (Exception e) {
            logger.warn("Impossible de lire {} dans {} : {}", fileName, projectPath, e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TARGET RESOLUTION — mirrors ProjectStructureTool.resolveTarget()
    // ═══════════════════════════════════════════════════════════════════════════
    private record ResolvedTarget(String owner, String repo, String branch, String path, String error) {

        static ResolvedTarget of(String owner, String repo, String branch, String path) {
            return new ResolvedTarget(owner, repo, branch, path, null);
        }

        static ResolvedTarget err(String message) {
            return new ResolvedTarget(null, null, null, null, message);
        }
    }

    private ResolvedTarget resolveTarget(String repository, String branch) {
        String memOwner = memoryService.recall("current_owner");
        String memRepo = memoryService.recall("current_repo");
        String memBranch = memoryService.recall("current_branch");
        String memPath = memoryService.recall("current_path");

        Optional<GitProjectContext> liveCtx = projectContextService.detectFromCurrentDirectory();
        String liveOwner = liveCtx.map(GitProjectContext::owner).orElse(null);
        String liveRepo = liveCtx.map(GitProjectContext::repo).orElse(null);
        String liveBranch = liveCtx.map(GitProjectContext::branch).orElse(null);
        String livePath = liveCtx.map(GitProjectContext::projectPath).orElse(null);

        if (repository != null && !repository.isBlank()) {
            String[] parts = repository.trim().split("/");
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return ResolvedTarget.err(
                        "ERREUR : Le dépôt doit être au format 'owner/repo'.");
            }
            String targetOwner = parts[0].trim();
            String targetRepo = parts[1].trim();
            String targetPath = null;
            String targetBranch;

            if (liveOwner != null
                    && liveOwner.equalsIgnoreCase(targetOwner)
                    && liveRepo != null
                    && liveRepo.equalsIgnoreCase(targetRepo)) {
                targetPath = livePath;
                targetBranch = branch != null ? branch : liveBranch;
            } else if (memOwner != null
                    && memOwner.equalsIgnoreCase(targetOwner)
                    && memRepo != null
                    && memRepo.equalsIgnoreCase(targetRepo)) {
                targetPath = memPath;
                targetBranch = branch != null ? branch : memBranch;
            } else {
                targetBranch = branch != null ? branch : "main";
            }
            return ResolvedTarget.of(targetOwner, targetRepo,
                    targetBranch != null ? targetBranch : "main", targetPath);
        }

        if (memOwner != null) {
            return ResolvedTarget.of(memOwner, memRepo,
                    branch != null ? branch : memBranch, memPath);
        }

        if (liveOwner != null) {
            return ResolvedTarget.of(liveOwner, liveRepo,
                    branch != null ? branch : liveBranch, livePath);
        }

        return ResolvedTarget.err("""
                ERREUR : Aucun dépôt trouvé en mémoire et aucun projet détecté sur le disque.
                Solutions :
                • Lancez `initializeProject` pour détecter le projet courant
                • Fournissez 'owner/repo' explicitement en paramètre
                """);
    }
}
