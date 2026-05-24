package com.example.mcp_github.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.ComponentRole;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.analyzer.PatternDetector;
import com.example.mcp_github.service.analyzer.StackSignatureRegistry;

@Service
public class ProjectStructureAnalyzer {

    private final List<PatternDetector> detectors;
    private final StackSignatureRegistry stackRegistry;
    private final ComponentRoleResolver roleResolver;
    private final TreeSitterAnalyzer treeSitterAnalyzer;

    public ProjectStructureAnalyzer(
            List<PatternDetector> detectors,
            StackSignatureRegistry stackRegistry,
            ComponentRoleResolver roleResolver,
            TreeSitterAnalyzer treeSitterAnalyzer) {
        this.detectors = detectors;
        this.stackRegistry = stackRegistry;
        this.roleResolver = roleResolver;
        this.treeSitterAnalyzer = treeSitterAnalyzer;
    }

    private static final int CONFIDENCE_THRESHOLD = 70;

    public AnalysisResult analyze(RepositorySnapshot snapshot) {
        List<String> paths = snapshot.allPaths();
        Map<String, String> files = snapshot.keyFiles();

        Stack stack = detectStack(paths, files);
        List<PatternScore> scores = scorePatterns(paths);

        PatternScore best = scores.stream()
                .max((a, b) -> Integer.compare(a.score(), b.score()))
                .orElse(new PatternScore(ArchitecturePattern.UNKNOWN, 0, List.of()));

        List<String> packageSummary = extractPackageSummary(paths);
        List<String> suggestions = generateSuggestions(best.pattern(), paths, stack);

        // ── Inférence des rôles (heuristique sur les chemins) ─────────────────
        List<ComponentRole> inferredRoles = inferComponentRoles(paths, best.pattern());

        // ── Enrichissement AST (Tree-sitter) — analyse du contenu réel ───────
        var astSummaries = treeSitterAnalyzer.analyzeFiles(files);
        List<String> astSignals = treeSitterAnalyzer.extractArchitecturalSignals(astSummaries);

        // Fusionner les signaux AST avec les signaux heuristiques
        List<String> allSignals = new ArrayList<>(best.matchedSignals());
        for (String astSignal : astSignals) {
            if (!allSignals.contains(astSignal)) {
                allSignals.add(astSignal);
            }
        }

        // Bonus de confiance si l'AST confirme le pattern détecté
        int astConfidenceBonus = computeAstConfidenceBonus(best.pattern(), astSignals);
        int finalConfidence = Math.min(best.score() + astConfidenceBonus, 100);

        return new AnalysisResult(
                stack,
                best.pattern(),
                finalConfidence,
                allSignals,
                packageSummary,
                suggestions,
                finalConfidence < CONFIDENCE_THRESHOLD,
                scores,
                inferredRoles,
                astSummaries.isEmpty() ? null : treeSitterAnalyzer.buildAstReport(astSummaries));
    }

    /**
     * Calcule un bonus de confiance basé sur les signaux AST.
     * Si l'AST confirme des annotations cohérentes avec le pattern détecté,
     * on augmente la confiance de 5 à 15 points.
     */
    private int computeAstConfidenceBonus(ArchitecturePattern pattern, List<String> astSignals) {
        if (astSignals.isEmpty()) return 0;
        int bonus = 0;
        for (String signal : astSignals) {
            switch (pattern) {
                case MVC -> {
                    if (signal.contains("@RestController") || signal.contains("@Controller")) bonus += 5;
                    if (signal.contains("@Service"))    bonus += 3;
                    if (signal.contains("@Repository")) bonus += 3;
                }
                case CLEAN_ARCHITECTURE, HEXAGONAL -> {
                    if (signal.contains("@Service"))    bonus += 4;
                    if (signal.contains("@Repository")) bonus += 4;
                    if (signal.contains("Spring Data")) bonus += 5;
                }
                default -> {
                    if (signal.contains("Spring AI") || signal.contains("@Tool")) bonus += 5;
                }
            }
        }
        return Math.min(bonus, 15);
    }

    private Stack detectStack(List<String> paths, Map<String, String> files) {
        return new Stack(
                stackRegistry.detectLanguages(paths),
                stackRegistry.detectFrameworks(files),
                stackRegistry.detectBuildTools(paths));
    }

    private List<PatternScore> scorePatterns(List<String> paths) {
        return detectors.stream()
                .map(d -> d.score(paths))
                .toList();
    }

    private List<ComponentRole> inferComponentRoles(List<String> paths, ArchitecturePattern pattern) {
        return roleResolver.resolveRoles(paths, pattern);
    }

    private List<String> extractPackageSummary(List<String> paths) {
        Map<String, Integer> folderCounts = new HashMap<>();
        for (String path : paths) {
            String topFolder = path.contains("/") ? path.substring(0, path.indexOf('/')) : "(root)";
            folderCounts.merge(topFolder, 1, Integer::sum);
        }
        return folderCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> e.getKey() + "/ (" + e.getValue() + " files)")
                .toList();
    }

    private List<String> generateSuggestions(ArchitecturePattern pattern, List<String> paths, Stack stack) {
        List<String> suggestions = new ArrayList<>();
        if (pattern == ArchitecturePattern.UNKNOWN) {
            suggestions.add("Structure could not be confidently identified — consider reorganizing into a standard pattern.");
        }
        if (!paths.stream().anyMatch(p -> p.toLowerCase().contains("test"))) {
            suggestions.add("No test folder detected — consider adding unit tests.");
        }
        if (!paths.stream().anyMatch(p -> p.toLowerCase().endsWith("readme.md"))) {
            suggestions.add("No README.md found — consider adding one to document the project.");
        }
        return suggestions;
    }

    // RESULT MODELS

    public enum ArchitecturePattern {
        MVC, CLEAN_ARCHITECTURE, HEXAGONAL, LAYERED, FEATURE_BASED, MVVM, MICROSERVICES, UNKNOWN
    }

    public record Stack(List<String> languages, List<String> frameworks, List<String> buildTools) {}

    public record PatternScore(ArchitecturePattern pattern, int score, List<String> matchedSignals) {}

    public record AnalysisResult(
            Stack stack,
            ArchitecturePattern pattern,
            int confidence,
            List<String> matchedSignals,
            List<String> packageSummary,
            List<String> suggestions,
            boolean needsLlmFallback,
            List<PatternScore> allScores,
            List<ComponentRole> inferredRoles,
            /** Rapport AST généré par TreeSitterAnalyzer — null si aucun fichier analysable */
            String astReport) {
    }
}