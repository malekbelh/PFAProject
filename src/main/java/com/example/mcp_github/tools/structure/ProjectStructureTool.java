package com.example.mcp_github.tools.structure;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

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
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;
import com.example.mcp_github.service.ProjectStructureAnalyzer.Stack;
import com.example.mcp_github.service.RolloDocsBuilder;

@Component
public class ProjectStructureTool {

    private static final Logger logger = LoggerFactory.getLogger(ProjectStructureTool.class);

    private final GitHubFileTreeService gitHubFileTreeService;
    private final ProjectStructureAnalyzer analyzer;
    private final MemoryService memoryService;
    private final ProjectContextService projectContextService;
    private final ArchitecturePromptService promptService;
    private final DocumentationWriterService writerService;
    private final com.example.mcp_github.service.GitHubService githubService;
    private final RolloDocsBuilder rolloDocsBuilder;

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".py", ".go", ".rs", ".cs");

    public ProjectStructureTool(GitHubFileTreeService gitHubFileTreeService,
            ProjectStructureAnalyzer analyzer,
            MemoryService memoryService,
            ProjectContextService projectContextService,
            ArchitecturePromptService promptService,
            DocumentationWriterService writerService,
            com.example.mcp_github.service.GitHubService githubService,
            RolloDocsBuilder rolloDocsBuilder) {
        this.gitHubFileTreeService = gitHubFileTreeService;
        this.analyzer = analyzer;
        this.memoryService = memoryService;
        this.projectContextService = projectContextService;
        this.promptService = promptService;
        this.writerService = writerService;
        this.githubService = githubService;
        this.rolloDocsBuilder = rolloDocsBuilder;
    }

    @Tool(name = "analyzeProjectStructure", description = """
            ALWAYS use this tool when the user asks about project structure, architecture,
            design patterns, or how a repository is organized.

            Do NOT attempt to analyze code from context — this tool fetches live data
            directly from GitHub and produces accurate, up-to-date results.

            The repository can be inferred from memory (current_owner/current_repo)
            if the user does not specify one explicitly.

            File generation rules:
            - rollo.md           → general questions: "structure", "architecture globale"
            - rollo_detailled.md → ONLY when user explicitly says "détaillé", "detailed", "deep", "complet"

            The chat summary is written as the first section of the generated file.
            No content duplication between chat and documentation files.
            """)
    public String analyzeProjectStructure(
            @ToolParam(description = "GitHub repository 'owner/repo'. Omit if project is already initialized.", required = false) String repository,
            @ToolParam(description = "Branch name (default: from memory or 'main')", required = false) String branch,
            @ToolParam(description = "User prompt in natural language (optional).", required = false) String userPrompt,
            @ToolParam(description = "true ONLY when user explicitly asks for a detailed/deep analysis.", required = false) Boolean detailed) {

        logger.info("TOOL: analyzeProjectStructure — repository={} branch={} detailed={}", repository, branch, detailed);

        try {
            // ── 1. Résolution du projet ───────────────────────────────────────
            ProjectTarget target = resolveTarget(repository, branch);
            if (target.error() != null) {
                return target.error();
            }

            // ── 2. Récupération + analyse ─────────────────────────────────────
            RepositorySnapshot snapshot = gitHubFileTreeService.snapshot(
                    target.owner(), target.repo(), target.branch());

            if (snapshot.tree().isEmpty()) {
                return "ERREUR : Impossible de récupérer l'arborescence de `%s/%s` [%s]."
                        .formatted(target.owner(), target.repo(), target.branch());
            }

            AnalysisResult analysis = analyzer.analyze(snapshot);

            // ── 3. Génération des Services (rollo_docs/) & Empreinte ────────────
            com.example.mcp_github.model.ProjectFingerprint fingerprint = rolloDocsBuilder.build(
                    target.path(), target.owner(), target.repo(), target.branch(), snapshot, analysis);

            // ── 4. Prompt enrichi ─────────────────────────────────────────────
            String enhancedPrompt = promptService.generateEnhancedPrompt(fingerprint, userPrompt);

            // ── 4. Type de documentation ──────────────────────────────────────
            boolean isDetailed = resolveIsDetailed(detailed, userPrompt);

            // ── 5. Synthèse chat → sera la section "Synthèse" du fichier ─────
            String chatSummary = buildChatSummary(analysis, snapshot);

            // ── 6. Corps technique du fichier ─────────────────────────────────
            String docBody = isDetailed
                    ? generateDetailedDocumentation(analysis, snapshot, enhancedPrompt)
                    : generateStandardDocumentation(analysis, snapshot);

            // ── 7. Écriture : chatSummary + docBody dans un seul fichier ─────
            WriteResult result = writerService.write(
                    target.path(), chatSummary, docBody, isDetailed,
                    target.owner(), target.repo(), target.branch());

            // ── 8. Réponse chat : synthèse + confirmation fichier ─────────────
            String fileConfirmation;
            if (result instanceof WriteResult.Success s) {
                fileConfirmation = writerService.formatSuccessMessage(s, target.owner(), target.repo(), target.branch());
            } else if (result instanceof WriteResult.Skipped sk) {
                fileConfirmation = writerService.formatWarningMessage(sk.reason());
            } else if (result instanceof WriteResult.Failed f) {
                fileConfirmation = writerService.formatWarningMessage(f.reason());
            } else {
                fileConfirmation = "";
            }

            // La réponse chat = synthèse lisible + confirmation du fichier généré + le prompt pour l'IA
            return chatSummary + "\n\n---\n\n" + fileConfirmation + "\n\n" +
                   "System Instruction for LLM:\n" + enhancedPrompt;

        } catch (Exception e) {
            logger.error("analyzeProjectStructure a échoué", e);
            return "ERREUR : " + e.getMessage();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RÉSOLUTION DU PROJET
    // ═══════════════════════════════════════════════════════════════════════════
    private record ProjectTarget(String owner, String repo, String branch, String path, String error) {

        static ProjectTarget of(String owner, String repo, String branch, String path) {
            return new ProjectTarget(owner, repo, branch, path, null);
        }

        static ProjectTarget err(String message) {
            return new ProjectTarget(null, null, null, null, message);
        }
    }

    private ProjectTarget resolveTarget(String repository, String branch) {
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
                return ProjectTarget.err(
                        "ERREUR : Le dépôt doit être au format 'owner/repo' (ex. 'octocat/hello-world').");
            }
            String targetOwner = parts[0].trim();
            String targetRepo = parts[1].trim();
            String targetPath = null;
            String targetBranch;

            if (liveOwner != null
                    && liveOwner.equalsIgnoreCase(targetOwner)
                    && liveRepo.equalsIgnoreCase(targetRepo)) {
                targetPath = livePath;
                targetBranch = branch != null ? branch : liveBranch;
            } else if (memOwner != null
                    && memOwner.equalsIgnoreCase(targetOwner)
                    && memRepo.equalsIgnoreCase(targetRepo)) {
                targetPath = memPath;
                targetBranch = branch != null ? branch : memBranch;
            } else {
                targetBranch = branch != null ? branch : "main";
            }
            return ProjectTarget.of(targetOwner, targetRepo,
                    targetBranch != null ? targetBranch : "main", targetPath);
        }

        if (memOwner != null) {
            return ProjectTarget.of(memOwner, memRepo,
                    branch != null ? branch : memBranch, memPath);
        }

        if (liveOwner != null) {
            syncMemory(liveOwner, liveRepo, branch != null ? branch : liveBranch, livePath);
            return ProjectTarget.of(liveOwner, liveRepo,
                    branch != null ? branch : liveBranch, livePath);
        }

        return ProjectTarget.err("""
                ERREUR : Aucun dépôt trouvé en mémoire et aucun projet détecté sur le disque.
                Solutions :
                • Lancez `initializeProject` pour détecter le projet courant
                • Lancez `detectProjectFromPath("/chemin/absolu")`
                • Fournissez 'owner/repo' explicitement en paramètre
                """);
    }

    private boolean resolveIsDetailed(Boolean detailed, String userPrompt) {
        if (Boolean.TRUE.equals(detailed)) {
            return true;
        }
        String pl = userPrompt != null ? userPrompt.toLowerCase() : "";
        return pl.contains("détaillé") || pl.contains("detailled")
                || pl.contains("detailed") || pl.contains("deep")
                || pl.contains("complet");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNTHÈSE CHAT — écrite aussi comme section "Synthèse" dans le fichier
    // ═══════════════════════════════════════════════════════════════════════════
    /**
     * Génère la synthèse lisible destinée au chat. Elle est aussi injectée en
     * tête du fichier généré (rollo.md / rollo_detailled.md) via
     * DocumentationWriterService.assembleFull(). Pas de duplication : un seul
     * endroit où ce contenu est produit.
     */
    private String buildChatSummary(AnalysisResult analysis, RepositorySnapshot snapshot) {
        StringBuilder r = new StringBuilder();

        r.append("**Dépôt :** `%s/%s` · **Branche :** `%s` · **Fichiers :** %d\n\n"
                .formatted(snapshot.owner(), snapshot.repo(),
                        snapshot.branch(), snapshot.tree().size()));

        Stack stack = analysis.stack();
        r.append("**Stack :**");
        if (!stack.languages().isEmpty()) {
            r.append(" %s".formatted(String.join(", ", stack.languages())));
        }
        if (!stack.frameworks().isEmpty()) {
            r.append(" · %s".formatted(String.join(", ", stack.frameworks())));
        }
        if (!stack.buildTools().isEmpty()) {
            r.append(" · %s".formatted(String.join(", ", stack.buildTools())));
        }
        r.append("\n\n");

        r.append("**Pattern :** %s · **Confiance :** %d%%\n\n"
                .formatted(formatPatternName(analysis.pattern()), analysis.confidence()));

        if (!analysis.matchedSignals().isEmpty()) {
            r.append("**Signaux détectés :**\n");
            analysis.matchedSignals().forEach(s -> r.append("- ✓ %s\n".formatted(s)));
            r.append("\n");
        }

        if (!analysis.suggestions().isEmpty()) {
            r.append("**Recommandations :**\n");
            analysis.suggestions().stream().limit(5)
                    .forEach(s -> r.append("- %s\n".formatted(s)));
            if (analysis.suggestions().size() > 5) {
                r.append("- *...%d autres dans le fichier généré*\n"
                        .formatted(analysis.suggestions().size() - 5));
            }
        } else {
            r.append("✓ Architecture bien structurée — aucun problème critique détecté.\n");
        }

        return r.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DOCUMENTATION STANDARD — corps technique de rollo.md
    // ═══════════════════════════════════════════════════════════════════════════
    private String generateStandardDocumentation(AnalysisResult analysis,
            RepositorySnapshot snapshot) {
        StringBuilder d = new StringBuilder();
        String now = LocalDate.now().toString();
        Stack stack = analysis.stack();

        d.append("# Architecture — `%s/%s`\n\n".formatted(snapshot.owner(), snapshot.repo()));
        d.append("> Branche `%s` · généré le `%s`\n\n".formatted(snapshot.branch(), now));

        d.append("---\n\n## Stack Technologique\n\n");
        d.append("| Catégorie | Technologie |\n|-----------|-------------|\n");
        if (!stack.languages().isEmpty()) {
            d.append("| Langages | %s |\n".formatted(String.join(", ", stack.languages())));
        }
        if (!stack.frameworks().isEmpty()) {
            d.append("| Frameworks | %s |\n".formatted(String.join(", ", stack.frameworks())));
        }
        if (!stack.buildTools().isEmpty()) {
            d.append("| Outils Build | %s |\n".formatted(String.join(", ", stack.buildTools())));
        }

        d.append("\n---\n\n## Pattern : %s\n\n".formatted(formatPatternName(analysis.pattern())));
        d.append("**Confiance :** %d%%\n\n".formatted(analysis.confidence()));
        analysis.matchedSignals().forEach(s -> d.append("- ✓ %s\n".formatted(s)));
        d.append("\n").append(generateLayerDescription(analysis));

        d.append("\n---\n\n## Diagramme de Composants\n\n```mermaid\n")
                .append(generateMermaidTopology(snapshot, analysis)).append("```\n\n---\n\n");

        d.append("## Structure du Projet\n\n```\n");
        generateDirectoryTree(snapshot, d);
        d.append("```\n\n---\n\n## Composants\n\n");
        generateComponentBreakdown(snapshot, d);

        d.append("---\n\n## Flux de Données\n\n```\n")
                .append(generateDataFlow(analysis)).append("```\n\n---\n\n");

        d.append("## Recommandations\n\n");
        if (analysis.suggestions().isEmpty()) {
            d.append("✓ Architecture bien structurée.\n");
        } else {
            analysis.suggestions().forEach(s -> d.append("- %s\n".formatted(s)));
        }

        d.append("\n\n---\n*Généré par GitHub MCP Server — %s*\n".formatted(now));
        return d.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DOCUMENTATION DÉTAILLÉE — corps technique de rollo_detailled.md
    // ═══════════════════════════════════════════════════════════════════════════
    private String generateDetailedDocumentation(AnalysisResult analysis,
            RepositorySnapshot snapshot, String enhancedPrompt) {
        StringBuilder d = new StringBuilder();
        String now = LocalDate.now().toString();
        Stack stack = analysis.stack();

        d.append("# Rapport Architecte Senior — `%s/%s`\n\n"
                .formatted(snapshot.owner(), snapshot.repo()));
        d.append("| Champ | Valeur |\n|-------|--------|\n");
        d.append("| Branche | `%s` |\n".formatted(snapshot.branch()));
        d.append("| Généré le | `%s` |\n".formatted(now));
        d.append("| Pattern détecté | %s |\n".formatted(formatPatternName(analysis.pattern())));
        d.append("| Confiance | %d%% |\n".formatted(analysis.confidence()));
        d.append("| Total fichiers | %d |\n\n".formatted(snapshot.tree().size()));
        d.append("---\n\n");

        d.append("## 1. Résumé Exécutif\n\n")
                .append(buildExecutiveSummary(analysis, snapshot)).append("\n---\n\n");
        d.append("## 2. Stack Technologique\n\n")
                .append(buildStackDeepDive(stack, snapshot)).append("\n---\n\n");
        d.append("## 3. Matrice de Confiance des Patterns\n\n")
                .append(buildPatternMatrix(analysis)).append("\n---\n\n");

        d.append("## 4. Vues Architecturales (4+1)\n\n");
        d.append("### 4.1 Vue Logique\n\n```mermaid\n")
                .append(generateMermaidTopology(snapshot, analysis)).append("```\n\n");

        d.append("### 4.3 Vue Développement\n\n```mermaid\n")
                .append(buildDependencyGraph(snapshot, analysis)).append("```\n\n---\n\n");

        d.append("## 5. Structure Complète\n\n```\n");
        buildFullDirectoryTree(snapshot, d);
        d.append("```\n\n---\n\n");

        d.append("## 6. Catalogue des Composants\n\n");
        buildDetailedComponentCatalogue(snapshot, d);
        d.append("---\n\n");

        d.append("## 7. Préoccupations Transversales\n\n")
                .append(buildCrossCuttingConcerns(analysis, snapshot, stack)).append("\n---\n\n");
        d.append("## 8. Couplage & Cohésion\n\n")
                .append(buildCouplingAnalysis(analysis, snapshot)).append("\n---\n\n");

        d.append("## 9. Tableau de Bord Qualité\n\n");
        buildCodeQualityDashboard(snapshot, analysis, d);
        d.append("\n---\n\n");

        d.append("## 10. Revue Sécurité\n\n")
                .append(buildSecurityReview(analysis, snapshot, stack)).append("\n---\n\n");
        d.append("## 11. Scalabilité & Performance\n\n")
                .append(buildScalabilityAssessment(analysis, stack, snapshot)).append("\n---\n\n");

        d.append("## 12. Recommandations Stratégiques\n\n");
        buildStrategicRecommendations(analysis, stack, d);
        d.append("\n---\n\n");

        d.append("## 13. Feuille de Route\n\n");
        buildImprovementRoadmap(analysis, stack, d);
        d.append("\n---\n\n");

        d.append("## 14. Architecture Decision Records (ADR)\n\n")
                .append(buildADRs(analysis, stack)).append("\n---\n\n");

        d.append("*Dernière mise à jour : %s*\n".formatted(now));
        return d.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIAGRAMMES MERMAID
    // ═══════════════════════════════════════════════════════════════════════════
    private String generateMermaidTopology(RepositorySnapshot s, AnalysisResult a) {
        StringBuilder m = new StringBuilder("graph TD\n");

        Map<String, String> categoryToId = new LinkedHashMap<>();
        Map<String, List<String>> categoryFiles = new LinkedHashMap<>();

        for (String path : s.allPaths()) {
            if (!isSource(path)) {
                continue;
            }
            String cat = categorise(path);
            String nodeId = escapeMermaid(cat);
            categoryToId.put(cat, nodeId);
            categoryFiles.computeIfAbsent(cat, k -> new ArrayList<>()).add(clsName(path));
        }

        List<String> order = List.of(
                "Controllers", "MCP Tools", "Services", "Use Cases", "Domain",
                "Models / Entities", "Repositories", "Infrastructure",
                "Configuration", "Security", "Utilities");

        for (String cat : order) {
            if (!categoryToId.containsKey(cat)) {
                continue;
            }
            List<String> files = categoryFiles.get(cat).stream().distinct().limit(6).toList();
            String nodeId = categoryToId.get(cat);
            appendSubgraph(m, cat, nodeId, files);
        }

        if (categoryFiles.containsKey("Other") && !categoryFiles.get("Other").isEmpty()) {
            List<String> files = categoryFiles.get("Other").stream().distinct().limit(6).toList();
            appendSubgraph(m, "Other", "Other", files);
        }

        String ctrlId = categoryToId.getOrDefault("Controllers", categoryToId.get("MCP Tools"));
        String svcId = categoryToId.get("Services");
        String repoId = categoryToId.get("Repositories");
        String ucId = categoryToId.get("Use Cases");
        String domId = categoryToId.get("Domain");
        String infraId = categoryToId.get("Infrastructure");
        String entId = categoryToId.get("Models / Entities");

        if (ctrlId != null && svcId != null) {
            m.append("    %s --> %s\n".formatted(ctrlId, svcId));
        }
        if (ctrlId != null && ucId != null) {
            m.append("    %s --> %s\n".formatted(ctrlId, ucId));
        }
        if (svcId != null && repoId != null) {
            m.append("    %s --> %s\n".formatted(svcId, repoId));
        }
        if (svcId != null && entId != null) {
            m.append("    %s --> %s\n".formatted(svcId, entId));
        }
        if (ucId != null && domId != null) {
            m.append("    %s --> %s\n".formatted(ucId, domId));
        }
        if (infraId != null && domId != null) {
            m.append("    %s --> %s\n".formatted(infraId, domId));
        }
        if (infraId != null && repoId != null) {
            m.append("    %s --> %s\n".formatted(infraId, repoId));
        }
        if (repoId != null) {
            m.append("    %s --> DB[(\"Base de données\")]\n".formatted(repoId));
        }

        return m.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDERS SECTION DÉTAILLÉE
    // ═══════════════════════════════════════════════════════════════════════════
    private String buildExecutiveSummary(AnalysisResult analysis, RepositorySnapshot snapshot) {
        Stack stack = analysis.stack();
        long src = countByType(snapshot, "source");
        long tests = countByType(snapshot, "test");
        double ratio = src > 0 ? (double) tests / src * 100 : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("Ce dépôt implémente un projet **%s**"
                .formatted(stack.languages().isEmpty() ? "logiciel" : stack.languages().get(0)));
        if (!stack.frameworks().isEmpty()) {
            sb.append(" utilisant **%s**".formatted(stack.frameworks().get(0)));
        }
        sb.append(".\n\n");

        sb.append("L'architecture correspond au pattern **%s** (confiance **%d%%**). "
                .formatted(formatPatternName(analysis.pattern()), analysis.confidence()));
        sb.append(analysis.confidence() >= 80
                ? "Forte cohérence architecturale."
                : analysis.confidence() >= 50
                ? "Alignement architectural partiel."
                : "Pas d'alignement fort avec un pattern connu.");
        sb.append("\n\n");

        sb.append("| Métrique | Valeur | Évaluation |\n|---------|--------|------------|\n");
        sb.append("| Total fichiers | %d | |\n".formatted(snapshot.tree().size()));
        sb.append("| Fichiers source | %d | |\n".formatted(src));
        sb.append("| Fichiers test | %d | %.0f%% ratio |\n".formatted(tests, ratio));
        sb.append("| Confiance architecture | %d%% | %s |\n".formatted(analysis.confidence(),
                analysis.confidence() >= 70 ? "✅ Élevée"
                : analysis.confidence() >= 40 ? "⚠️ Moyenne" : "❌ Faible"));
        return sb.toString();
    }

    private String buildStackDeepDive(Stack stack, RepositorySnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Langages\n\n");
        sb.append("| Langage | Nb fichiers | Rôle |\n|---------|-------------|------|\n");
        for (String lang : stack.languages()) {
            String ext = langToExt(lang);
            long count = ext.isBlank() ? 0
                    : snapshot.allPaths().stream().filter(p -> p.endsWith(ext)).count();
            sb.append("| %s | %d | %s |\n".formatted(lang, count, inferLangRole(lang, stack)));
        }
        sb.append("\n### Frameworks\n\n");
        for (String fw : stack.frameworks()) {
            sb.append("#### %s\n\n%s\n".formatted(fw, describeFramework(fw)));
        }
        sb.append("### Build & Outils\n\n");
        for (String bt : stack.buildTools()) {
            sb.append("- **%s** — %s\n".formatted(bt, describeBuildTool(bt)));
        }
        return sb.toString();
    }

    private String buildPatternMatrix(AnalysisResult analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("| # | Pattern | Score | Barre | Signaux | Verdict |\n");
        sb.append("|---|---------|-------|-------|---------|--------|\n");
        List<PatternScore> sorted = new ArrayList<>(analysis.allScores());
        sorted.sort((a, b) -> Integer.compare(b.score(), a.score()));
        int rank = 1;
        for (PatternScore ps : sorted) {
            String bar = "█".repeat(ps.score() / 10) + "░".repeat(10 - ps.score() / 10);
            String verdict = ps.score() >= 70 ? "✅ Match principal"
                    : ps.score() >= 40 ? "⚠️ Partiel" : "— Absent";
            String signals = ps.matchedSignals().isEmpty() ? "aucun"
                    : String.join(", ", ps.matchedSignals());
            sb.append("| %d | %s | %d%% | `%s` | %s | %s |\n"
                    .formatted(rank++, formatPatternName(ps.pattern()),
                            ps.score(), bar, signals, verdict));
        }
        return sb.toString();
    }

    private String buildCrossCuttingConcerns(AnalysisResult analysis,
            RepositorySnapshot snapshot, Stack stack) {
        List<String> paths = snapshot.allPaths();
        StringBuilder sb = new StringBuilder();
        sb.append("| Préoccupation | Statut | Constat | Recommandation |\n"
                + "|--------------|--------|---------|----------------|\n");

        boolean hasLog = paths.stream().anyMatch(p -> p.toLowerCase().contains("log"));
        sb.append("| Logging | %s | %s | %s |\n".formatted(hasLog ? "✅" : "⚠️",
                hasLog ? "Logger présent" : "Pas de logging détecté",
                getLoggingRecommendation(stack)));

        boolean hasSec = stack.frameworks().contains("Spring Security")
                || paths.stream().anyMatch(p -> p.toLowerCase().contains("security")
                || p.toLowerCase().contains("jwt"));
        sb.append("| Sécurité / Auth | %s | %s | %s |\n".formatted(hasSec ? "✅" : "❌",
                hasSec ? "Couche sécurité détectée" : "Pas d'auth",
                getSecurityRecommendation(stack, hasSec)));

        boolean hasErr = paths.stream().anyMatch(p -> p.toLowerCase().contains("exception")
                || p.toLowerCase().contains("advice"));
        sb.append("| Gestion erreurs | %s | %s | %s |\n".formatted(hasErr ? "✅" : "⚠️",
                hasErr ? "Handler global détecté" : "Pas de handler global",
                getErrorHandlingRecommendation(stack)));

        boolean hasObs = snapshot.keyFiles().getOrDefault("pom.xml", "").contains("actuator")
                || paths.stream().anyMatch(p -> p.toLowerCase().contains("health"));
        sb.append("| Observabilité | %s | %s | %s |\n".formatted(hasObs ? "✅" : "⚠️",
                hasObs ? "Outils présents" : "Pas d'observabilité",
                getObservabilityRecommendation(stack)));
        return sb.toString();
    }

    private String buildCouplingAnalysis(AnalysisResult analysis, RepositorySnapshot snapshot) {
        List<String> paths = snapshot.allPaths();
        StringBuilder sb = new StringBuilder();
        sb.append("### Taille des couches\n\n");
        sb.append("| Couche | Fichiers | Risque |\n|--------|----------|--------|\n");
        long ctrl = countLayer(paths, List.of("controller", "controllers"));
        long svc = countLayer(paths, List.of("service", "services"));
        long repo = countLayer(paths, List.of("repository", "repositories", "dao"));
        long model = countLayer(paths, List.of("model", "models", "entity", "entities"));
        sb.append("| Controllers | %d | %s |\n".formatted(ctrl,
                ctrl > 20 ? "⚠️ Scinder en sous-packages" : "✅"));
        sb.append("| Services | %d | %s |\n".formatted(svc,
                svc > 30 ? "⚠️ Scinder par domaine" : "✅"));
        sb.append("| Repositories | %d | %s |\n".formatted(repo,
                repo > 20 ? "⚠️ Vérifier les fuites de logique" : "✅"));
        sb.append("| Models / Entities | %d | %s |\n".formatted(model,
                model > 50 ? "⚠️ Groupement domaine conseillé" : "✅"));
        return sb.toString();
    }

    private void buildCodeQualityDashboard(RepositorySnapshot snapshot,
            AnalysisResult analysis, StringBuilder d) {
        long src = countByType(snapshot, "source");
        long tests = countByType(snapshot, "test");
        double ratio = src > 0 ? (double) tests / src * 100 : 0;
        List<String> paths = snapshot.allPaths();

        d.append("| Signal | Statut | Constat | Action recommandée |\n");
        d.append("|--------|--------|---------|-------------------|\n");
        row(d, "Tests unitaires", tests > 0,
                tests > 0 ? "%d fichiers — %.0f%%".formatted(tests, ratio) : "Aucun fichier test",
                tests > 0 ? "Maintenir ratio > 50%" : "Ajouter des tests");
        row(d, "Couche DTO",
                paths.stream().anyMatch(p -> p.toLowerCase().contains("dto")),
                "Évite l'exposition des entités",
                "Tous les endpoints doivent utiliser des DTOs");
        row(d, "Handler d'exceptions global",
                paths.stream().anyMatch(p -> p.toLowerCase().contains("exception")
                || p.toLowerCase().contains("advice")),
                "Centralise la gestion des erreurs",
                "Implémenter @ControllerAdvice");
        row(d, "README.md",
                paths.stream().anyMatch(p -> p.equalsIgnoreCase("readme.md")),
                "Onboarding développeur",
                "Documenter la configuration locale");
        row(d, "Pipeline CI/CD",
                paths.stream().anyMatch(p -> p.contains(".github/workflows")
                || p.contains("Jenkinsfile")),
                "Build/test automatisé",
                "Configurer un pipeline CI");
        row(d, "Docker",
                paths.stream().anyMatch(p -> p.equalsIgnoreCase("dockerfile")
                || p.toLowerCase().contains("docker-compose")),
                "Runtime reproductible",
                "Ajouter Dockerfile + docker-compose.yml");
    }

    private void row(StringBuilder d, String s, boolean ok, String finding, String action) {
        d.append("| %s | %s | %s | %s |\n".formatted(s, ok ? "✅" : "⚠️", finding, action));
    }

    private String buildSecurityReview(AnalysisResult analysis,
            RepositorySnapshot snapshot, Stack stack) {
        List<String> paths = snapshot.allPaths();
        StringBuilder sb = new StringBuilder();
        sb.append("| Mécanisme | Détecté | Notes |\n|-----------|---------|-------|\n");
        boolean hasSpringSec = stack.frameworks().contains("Spring Security");
        boolean hasJwt = paths.stream().anyMatch(p -> p.toLowerCase().contains("jwt")
                || p.toLowerCase().contains("auth"));
        if (stack.frameworks().contains("Spring Boot")) {
            sb.append("| Spring Security | %s | %s |\n".formatted(hasSpringSec ? "✅" : "❌",
                    hasSpringSec ? "Présent" : "Ajouter spring-boot-starter-security"));
        }
        sb.append("| JWT | %s | %s |\n".formatted(hasJwt ? "✅" : "⚠️",
                hasJwt ? "Auth par token détectée" : "Envisager JWT"));
        sb.append("\n### Checklist Sécurité\n\n");
        sb.append("- [ ] HTTPS forcé en production\n"
                + "- [ ] CORS configuré restrictivement\n"
                + "- [ ] Secrets dans les variables d'env\n"
                + "- [ ] Rate limiting sur les endpoints publics\n");
        return sb.toString();
    }

    private String buildScalabilityAssessment(AnalysisResult analysis,
            Stack stack, RepositorySnapshot snapshot) {
        List<String> paths = snapshot.allPaths();
        StringBuilder sb = new StringBuilder();
        sb.append("| Signal | Statut | Impact |\n|--------|--------|--------|\n");
        boolean hasAsync = paths.stream().anyMatch(p -> p.toLowerCase().contains("async")
                || p.toLowerCase().contains("kafka"));
        boolean hasCache = paths.stream().anyMatch(p -> p.toLowerCase().contains("cache")
                || p.toLowerCase().contains("redis"));
        boolean hasDocker = paths.stream().anyMatch(p -> p.equalsIgnoreCase("dockerfile")
                || p.toLowerCase().contains("docker-compose"));
        sb.append("| Async / MQ | %s | Allège les tâches lourdes |\n".formatted(hasAsync ? "✅" : "⚠️"));
        sb.append("| Cache | %s | Réduit la charge DB |\n".formatted(hasCache ? "✅" : "⚠️"));
        sb.append("| Docker | %s | Permet le scaling horizontal |\n".formatted(hasDocker ? "✅" : "⚠️"));
        return sb.toString();
    }

    private void buildStrategicRecommendations(AnalysisResult analysis,
            Stack stack, StringBuilder d) {
        d.append("### Critique (à corriger immédiatement)\n\n");
        boolean had = false;
        for (String s : analysis.suggestions()) {
            d.append("- [ ] %s\n".formatted(s));
            had = true;
        }
        if (!had) {
            d.append("Aucun problème architectural critique détecté.\n");
        }
        if (stack.frameworks().contains("Spring Boot")) {
            d.append("\n### Spécificités Spring Boot\n\n");
            d.append("- [ ] `@ConfigurationProperties` + `@Validated` plutôt que `@Value`.\n");
            d.append("- [ ] `spring.jpa.open-in-view=false`.\n");
            d.append("- [ ] `@Transactional` uniquement sur la couche service.\n");
        }
    }

    private void buildImprovementRoadmap(AnalysisResult analysis,
            Stack stack, StringBuilder d) {
        d.append("| Phase | Horizon | Objectif | Livrables | Métrique |\n");
        d.append("|-------|---------|----------|-----------|----------|\n");
        d.append("| **1 — Stabiliser** | Semaine 1–2 | Combler les lacunes | README, tests, handler exceptions, DTOs | Build vert |\n");
        d.append("| **2 — Qualité** | Semaine 3–4 | Quality gate | CI/CD, OpenAPI, logging structuré | CI vert sur chaque PR |\n");
        d.append("| **3 — Observabilité** | Mois 2 | Prêt prod | Actuator, métriques, health probes | MTTR < 15 min |\n");
        d.append("| **4 — Scale** | Mois 3+ | Charge ×10 | Cache, async, optimisation DB | p99 < 200ms |\n");
    }

    private String buildADRs(AnalysisResult analysis, Stack stack) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ADR-001 : Choix du Pattern Architectural\n\n");
        sb.append("**Statut :** Accepté\n\n");
        sb.append("**Décision :** Adopter **%s**.\n\n"
                .formatted(formatPatternName(analysis.pattern())));
        sb.append("**Conséquences :** (+) Pattern bien compris. (-) Discipline requise.\n\n");
        if (!stack.frameworks().isEmpty()) {
            sb.append("### ADR-002 : Choix du Framework\n\n");
            sb.append("**Décision :** Utiliser **%s**.\n\n".formatted(stack.frameworks().get(0)));
        }
        sb.append("### ADR-003 : Stratégie de Tests\n\n");
        sb.append("**Décision :** Pyramide : Unitaires (70%) + Intégration (20%) + Contrat (10%).\n\n");
        return sb.toString();
    }

    private String buildDependencyGraph(RepositorySnapshot snapshot, AnalysisResult analysis) {
        StringBuilder m = new StringBuilder("graph LR\n");
        snapshot.allPaths().stream()
                .filter(p -> p.contains("/"))
                .map(p -> p.substring(0, p.indexOf('/')))
                .distinct().filter(d -> !d.startsWith(".")).limit(10)
                .forEach(dir -> m.append("    %s[\"%s\"]\n".formatted(escapeMermaid(dir), dir)));
        return m.toString();
    }

    private void buildFullDirectoryTree(RepositorySnapshot snapshot, StringBuilder d) {
        Map<String, List<String>> byDir = new TreeMap<>();
        for (String path : snapshot.allPaths()) {
            String top = path.contains("/") ? path.substring(0, path.indexOf('/')) : "(root)";
            byDir.computeIfAbsent(top, k -> new ArrayList<>()).add(path);
        }
        for (Map.Entry<String, List<String>> e : byDir.entrySet()) {
            d.append("%s/ (%d fichiers)\n".formatted(e.getKey(), e.getValue().size()));
            e.getValue().stream().filter(this::isSource).limit(8).forEach(p -> {
                String rel = p.startsWith(e.getKey() + "/")
                        ? p.substring(e.getKey().length() + 1) : p;
                d.append("  ├── %s\n".formatted(rel));
            });
        }
    }

    private void generateDirectoryTree(RepositorySnapshot snapshot, StringBuilder d) {
        snapshot.allPaths().stream()
                .filter(p -> p.contains("/"))
                .map(p -> p.substring(0, p.indexOf('/'))).distinct().limit(15)
                .forEach(dir -> {
                    d.append("%s/\n".formatted(dir));
                    snapshot.allPaths().stream()
                            .filter(p -> p.startsWith(dir + "/") && isSource(p)).limit(5)
                            .forEach(p -> d.append("  ├── %s\n"
                            .formatted(p.substring(p.lastIndexOf('/') + 1))));
                });
    }

    private void generateComponentBreakdown(RepositorySnapshot snapshot, StringBuilder d) {
        Map<String, List<String>> cat = new LinkedHashMap<>();
        for (String path : snapshot.allPaths()) {
            if (!isSource(path)) {
                continue;
            }
            String cls = clsName(path);
            if (cls == null) {
                continue;
            }
            cat.computeIfAbsent(categorise(path), k -> new ArrayList<>()).add(cls);
        }
        for (Map.Entry<String, List<String>> e : cat.entrySet()) {
            d.append("### %s\n\n".formatted(e.getKey()));
            e.getValue().stream().limit(10)
                    .forEach(c -> d.append("- `%s`\n".formatted(c)));
            if (e.getValue().size() > 10) {
                d.append("- *...et %d de plus*\n".formatted(e.getValue().size() - 10));
            }
            d.append("\n");
        }
    }

    private void buildDetailedComponentCatalogue(RepositorySnapshot snapshot, StringBuilder d) {
        Map<String, List<String>> cat = new LinkedHashMap<>();
        for (String path : snapshot.allPaths()) {
            if (!isSource(path)) {
                continue;
            }
            String cls = clsName(path);
            if (cls == null) {
                continue;
            }
            cat.computeIfAbsent(categorise(path), k -> new ArrayList<>()).add(cls);
        }
        for (Map.Entry<String, List<String>> e : cat.entrySet()) {
            d.append("### %s (%d)\n\n".formatted(e.getKey(), e.getValue().size()));
            d.append("| Classe | Responsabilité inférée |\n|--------|------------------------|\n");
            e.getValue().stream().limit(25)
                    .forEach(cls -> d.append("| `%s` | %s |\n"
                    .formatted(cls, inferRole(cls, e.getKey()))));
            d.append("\n");
        }
    }

    private void appendSubgraph(StringBuilder m, String label,
            String nodeId, List<String> names) {
        m.append("    subgraph %s[\"%s\"]\n".formatted(nodeId, label));
        if (names.isEmpty()) {
            m.append("        %s_empty[\"—\"]\n".formatted(nodeId));
        } else {
            names.forEach(n -> m.append("        %s_%s[\"%s\"]\n"
                    .formatted(nodeId, escapeMermaid(n != null ? n : "?"), n != null ? n : "?")));
        }
        m.append("    end\n\n");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ═══════════════════════════════════════════════════════════════════════════
    private long countByType(RepositorySnapshot snapshot, String type) {
        return switch (type) {
            case "source" ->
                snapshot.allPaths().stream().filter(this::isSource).count();
            case "test" ->
                snapshot.allPaths().stream()
                .filter(p -> p.toLowerCase().contains("test")
                || p.toLowerCase().contains("spec")).count();
            case "config" ->
                snapshot.allPaths().stream()
                .filter(p -> p.endsWith(".xml") || p.endsWith(".yml")
                || p.endsWith(".yaml") || p.endsWith(".properties")
                || p.endsWith(".toml") || p.endsWith(".json")).count();
            default ->
                0;
        };
    }

    private long countLayer(List<String> paths, List<String> folders) {
        return paths.stream().filter(p -> {
            String lp = p.toLowerCase();
            return folders.stream().anyMatch(f -> lp.contains("/" + f + "/")
                    || lp.startsWith(f + "/"));
        }).count();
    }

    private String categorise(String path) {
        String l = path.toLowerCase();
        if (l.contains("controller")) {
            return "Controllers";
        }
        if (l.contains("service")) {
            return "Services";
        }
        if (l.contains("repository") || l.contains("dao")) {
            return "Repositories";
        }
        if (l.contains("model") || l.contains("entity")) {
            return "Models / Entities";
        }
        if (l.contains("dto") || l.contains("request")
                || l.contains("response")) {
            return "DTOs";
        }
        if (l.contains("config") || l.contains("configuration")) {
            return "Configuration";
        }
        if (l.contains("util") || l.contains("helper")) {
            return "Utilities";
        }
        if (l.contains("exception") || l.contains("error")) {
            return "Exception Handling";
        }
        if (l.contains("test") || l.contains("spec")) {
            return "Tests";
        }
        if (l.contains("security") || l.contains("auth")) {
            return "Security";
        }
        if (l.contains("tool")) {
            return "MCP Tools";
        }
        return "Other";
    }

    private String inferRole(String cls, String cat) {
        return switch (cat) {
            case "Controllers" ->
                "Point d'entrée HTTP — valider, déléguer au service";
            case "Services" ->
                "Logique métier — propriétaire de @Transactional";
            case "Repositories" ->
                "Accès données — requêtes et persistance via ORM";
            case "Models / Entities" ->
                "Objet domaine — encapsule un concept métier";
            case "DTOs" ->
                "Objet de transfert — contrat API requête/réponse";
            case "Configuration" ->
                "Bean Spring ou configuration applicative";
            case "Utilities" ->
                "Helper stateless — partagé entre les couches";
            case "Exception Handling" ->
                "Type d'erreur ou handler global (@ControllerAdvice)";
            case "Tests" ->
                "Suite de tests ou fixture";
            case "Security" ->
                "Composant d'authentification ou d'autorisation";
            case "MCP Tools" ->
                "Tool IA exposé via Spring AI MCP";
            default ->
                humanise(cls);
        };
    }

    private String generateLayerDescription(AnalysisResult a) {
        return switch (a.pattern()) {
            case MVC ->
                """
                    - **Controller** — routing HTTP, sérialisation, validation.
                    - **Service** — règles métier, transactions.
                    - **Repository** — persistance et mapping ORM.
                    - **Model** — entités et objets valeur.
                    """;
            case CLEAN_ARCHITECTURE ->
                """
                    - **Entities** — règles métier d'entreprise.
                    - **Use Cases** — règles applicatives.
                    - **Interface Adapters** — controllers, gateways.
                    - **Frameworks & Drivers** — DB, web, APIs externes.
                    """;
            default ->
                """
                    - **Présentation** — interaction utilisateur et API.
                    - **Métier** — logique cœur.
                    - **Données** — persistance et intégrations.
                    """;
        };
    }

    private String generateDataFlow(AnalysisResult a) {
        return switch (a.pattern()) {
            case MVC ->
                """
                    Requête HTTP
                         ↓  Controller : valider + déléguer
                         ↓  Service : appliquer les règles métier
                         ↓  Repository : query / persist
                         ↓  Base de données
                         ↑  Objet domaine retourné
                         ↑  DTO mappé dans le service
                         ↑  JSON sérialisé
                    Réponse HTTP
                    """;
            default ->
                "Requête → API → Logique → Persistance → Réponse\n";
        };
    }

    private String formatPatternName(ArchitecturePattern p) {
        return switch (p) {
            case MVC ->
                "MVC (Model-View-Controller)";
            case CLEAN_ARCHITECTURE ->
                "Clean Architecture";
            case HEXAGONAL ->
                "Architecture Hexagonale (Ports & Adapters)";
            case LAYERED ->
                "Architecture en Couches";
            case FEATURE_BASED ->
                "Architecture Feature-Based / Modulaire";
            case MVVM ->
                "MVVM (Model-View-ViewModel)";
            case MICROSERVICES ->
                "Architecture Microservices";
            default ->
                "Pattern Inconnu";
        };
    }

    private String getLoggingRecommendation(Stack stack) {
        if (stack.frameworks().contains("Spring Boot")) {
            return "Utiliser SLF4J + Logback.";
        }
        if (stack.frameworks().contains("Express")) {
            return "Utiliser Winston ou Morgan.";
        }
        return "Utiliser un framework de logging structuré.";
    }

    private String getSecurityRecommendation(Stack stack, boolean hasSec) {
        if (hasSec) {
            return "Vérifier les politiques d'auth et l'expiration des tokens.";
        }
        if (stack.frameworks().contains("Spring Boot")) {
            return "Ajouter spring-boot-starter-security + JWT.";
        }
        return "Implémenter une couche d'authentification robuste.";
    }

    private String getErrorHandlingRecommendation(Stack stack) {
        if (stack.frameworks().contains("Spring Boot")) {
            return "@ControllerAdvice + RFC 7807 ProblemDetail.";
        }
        return "Implémenter un handler d'erreurs centralisé.";
    }

    private String getObservabilityRecommendation(Stack stack) {
        if (stack.frameworks().contains("Spring Boot")) {
            return "Ajouter Actuator + Micrometer.";
        }
        return "Implémenter des health checks et des métriques.";
    }

    private String describeFramework(String fw) {
        return switch (fw) {
            case "Spring Boot" ->
                "Auto-configuration + serveur embarqué.\n";
            case "Spring Security" ->
                "Auth, autorisation, protection CSRF.\n";
            case "React" ->
                "UI par composants fonctionnels + hooks.\n";
            default ->
                "Framework détecté dans le manifeste de dépendances.\n";
        };
    }

    private String describeBuildTool(String bt) {
        return switch (bt) {
            case "Maven" ->
                "compile → test → package → install → deploy.";
            case "Gradle" ->
                "Builds incrémentaux avec cache.";
            case "npm/yarn" ->
                "Committer le lock file. Versions exactes pour builds reproductibles.";
            default ->
                "Outil de gestion des dépendances.";
        };
    }

    private String langToExt(String lang) {
        return switch (lang) {
            case "Java" ->
                ".java";
            case "Kotlin" ->
                ".kt";
            case "TypeScript" ->
                ".ts";
            case "JavaScript" ->
                ".js";
            case "Python" ->
                ".py";
            case "Go" ->
                ".go";
            case "Rust" ->
                ".rs";
            case "C#" ->
                ".cs";
            default ->
                "";
        };
    }

    private String inferLangRole(String lang, Stack stack) {
        if (("TypeScript".equals(lang) || "JavaScript".equals(lang))
                && (stack.frameworks().contains("React")
                || stack.frameworks().contains("Next.js"))) {
            return "Frontend UI";
        }
        if ("Java".equals(lang) || "Kotlin".equals(lang)) {
            return "Backend / côté serveur";
        }
        if ("Python".equals(lang)) {
            return "Backend ou scripting";
        }
        return "Code applicatif";
    }

    private boolean isSource(String path) {
        return SOURCE_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private String clsName(String path) {
        String f = path.substring(path.lastIndexOf('/') + 1);
        for (String ext : SOURCE_EXTENSIONS) {
            if (f.endsWith(ext)) {
                return f.substring(0, f.length() - ext.length());
            }
        }
        return null;
    }

    private String humanise(String cls) {
        return cls.replaceAll("([A-Z])", " $1").trim();
    }

    private String escapeMermaid(String s) {
        return s.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private void syncMemory(String owner, String repo, String branch, String path) {
        if (owner != null) {
            memoryService.remember("current_owner", owner);
        }
        if (repo != null) {
            memoryService.remember("current_repo", repo);
        }
        if (branch != null) {
            memoryService.remember("current_branch", branch);
        }
        if (path != null) {
            memoryService.remember("current_path", path);
        }
    }

}
