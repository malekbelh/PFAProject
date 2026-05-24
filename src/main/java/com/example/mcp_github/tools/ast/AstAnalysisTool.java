package com.example.mcp_github.tools.ast;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_github.service.GitHubFileTreeService;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.MemoryService;
import com.example.mcp_github.service.TreeSitterAnalyzer;
import com.example.mcp_github.service.TreeSitterAnalyzer.AstFileSummary;

/**
 * AstAnalysisTool — Outil MCP d'analyse AST (Tree-sitter style).
 *
 * Expose l'analyse AST du contenu réel des fichiers source via le protocole MCP.
 * Contrairement à analyzeProjectStructure qui travaille sur les noms de fichiers,
 * cet outil parse le contenu des fichiers pour extraire :
 * - Classes, interfaces, méthodes publiques
 * - Annotations Spring (@Service, @RestController, @Tool, etc.)
 * - Imports et dépendances
 * - Signaux architecturaux concrets
 */
@Component
public class AstAnalysisTool {

    private static final Logger log = LoggerFactory.getLogger(AstAnalysisTool.class);

    private final GitHubFileTreeService gitHubFileTreeService;
    private final TreeSitterAnalyzer treeSitterAnalyzer;
    private final MemoryService memoryService;

    public AstAnalysisTool(
            GitHubFileTreeService gitHubFileTreeService,
            TreeSitterAnalyzer treeSitterAnalyzer,
            MemoryService memoryService) {
        this.gitHubFileTreeService = gitHubFileTreeService;
        this.treeSitterAnalyzer = treeSitterAnalyzer;
        this.memoryService = memoryService;
    }

    @Tool(name = "analyzeCodeAst", description = """
            Analyse le contenu AST (Abstract Syntax Tree) des fichiers source d'un dépôt GitHub.

            Contrairement à analyzeProjectStructure qui se base sur les noms de fichiers/dossiers,
            cet outil parse le CONTENU RÉEL des fichiers pour extraire :
            - Les classes, interfaces et méthodes déclarées
            - Les annotations Spring (@Service, @RestController, @Repository, @Tool, etc.)
            - Les imports et dépendances entre composants
            - Les signaux architecturaux concrets (ex: présence de @RestController confirmée dans le code)

            Langages supportés : Java (via JavaParser — AST complet), TypeScript, JavaScript, Python.

            Utiliser cet outil quand :
            - L'utilisateur demande "quelles classes sont dans ce fichier ?"
            - L'utilisateur veut connaître les méthodes publiques d'un service
            - On veut confirmer les annotations Spring réellement présentes dans le code
            - On veut une analyse plus précise que la détection par noms de dossiers

            Le dépôt est résolu depuis la mémoire si non spécifié.
            """)
    public String analyzeCodeAst(
            @ToolParam(description = "GitHub repository 'owner/repo'. Omit if already in memory.", required = false)
            String repository,
            @ToolParam(description = "Branch name (default: from memory or 'main')", required = false)
            String branch,
            @ToolParam(description = "Chemin d'un fichier spécifique à analyser (ex: src/main/java/com/example/MyService.java). Si omis, analyse tous les fichiers clés.", required = false)
            String filePath) {

        log.info("TOOL: analyzeCodeAst — repository={} branch={} filePath={}", repository, branch, filePath);

        try {
            // ── 1. Résolution du projet ───────────────────────────────────────
            String owner = repository != null ? repository.split("/")[0]
                    : memoryService.recall("current_owner");
            String repo = repository != null && repository.contains("/")
                    ? repository.split("/")[1]
                    : memoryService.recall("current_repo");
            String resolvedBranch = branch != null ? branch
                    : memoryService.recall("current_branch");

            if (owner == null || repo == null) {
                return """
                        ERREUR : Aucun dépôt trouvé en mémoire.
                        Lancez `initializeProject` ou fournissez 'owner/repo' explicitement.
                        """;
            }
            if (resolvedBranch == null) resolvedBranch = "main";

            // ── 2. Récupération du snapshot ───────────────────────────────────
            RepositorySnapshot snapshot = gitHubFileTreeService.snapshot(owner, repo, resolvedBranch);

            if (snapshot.tree().isEmpty()) {
                return "ERREUR : Impossible de récupérer l'arborescence de `%s/%s` [%s]."
                        .formatted(owner, repo, resolvedBranch);
            }

            // ── 3. Sélection des fichiers à analyser ──────────────────────────
            Map<String, String> filesToAnalyze;
            if (filePath != null && !filePath.isBlank()) {
                // Analyse d'un fichier spécifique
                String content = gitHubFileTreeService.fetchFileContent(owner, repo, resolvedBranch, filePath);
                if (content == null) {
                    return "ERREUR : Fichier `%s` introuvable dans `%s/%s` [%s]."
                            .formatted(filePath, owner, repo, resolvedBranch);
                }
                filesToAnalyze = Map.of(filePath, content);
            } else {
                // Analyse des fichiers clés déjà récupérés
                filesToAnalyze = snapshot.keyFiles();
            }

            if (filesToAnalyze.isEmpty()) {
                return "Aucun fichier source disponible pour l'analyse AST dans `%s/%s`."
                        .formatted(owner, repo);
            }

            // ── 4. Analyse AST ────────────────────────────────────────────────
            Map<String, AstFileSummary> summaries = treeSitterAnalyzer.analyzeFiles(filesToAnalyze);

            if (summaries.isEmpty()) {
                return "Aucun fichier analysable (Java/TS/JS/Python) trouvé dans les fichiers clés.";
            }

            // ── 5. Construction du rapport ────────────────────────────────────
            StringBuilder report = new StringBuilder();
            report.append("# Analyse AST — `%s/%s` [%s]\n\n".formatted(owner, repo, resolvedBranch));
            report.append("**Fichiers analysés :** %d\n\n".formatted(summaries.size()));

            // Signaux architecturaux extraits du contenu réel
            var signals = treeSitterAnalyzer.extractArchitecturalSignals(summaries);
            if (!signals.isEmpty()) {
                report.append("## Signaux architecturaux (depuis le contenu AST)\n\n");
                signals.forEach(s -> report.append("- ✓ ").append(s).append("\n"));
                report.append("\n");
            }

            // Rapport détaillé par fichier
            report.append(treeSitterAnalyzer.buildAstReport(summaries));

            return report.toString();

        } catch (Exception e) {
            log.error("analyzeCodeAst a échoué", e);
            return "ERREUR : " + e.getMessage();
        }
    }
}
