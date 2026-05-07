package com.example.mcp_github.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.analyzer.PatternDetector;
import com.example.mcp_github.service.analyzer.StackSignatureRegistry;

@Service
public class ProjectStructureAnalyzer {

    private final List<PatternDetector> detectors;
    private final StackSignatureRegistry stackRegistry;

    public ProjectStructureAnalyzer(List<PatternDetector> detectors, StackSignatureRegistry stackRegistry) {
        this.detectors = detectors;
        this.stackRegistry = stackRegistry;
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
        
        // IA Enrichment: Infer roles for better LLM context
        List<ComponentRole> roles = inferComponentRoles(paths);

        return new AnalysisResult(
                stack,
                best.pattern(),
                best.score(),
                best.matchedSignals(),
                packageSummary,
                suggestions,
                best.score() < CONFIDENCE_THRESHOLD,
                scores,
                roles);
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

    private List<ComponentRole> inferComponentRoles(List<String> paths) {
        List<ComponentRole> roles = new ArrayList<>();
        Map<String, String> patternToRole = Map.of(
            "controller", "Entry Point / API Routing",
            "service", "Business Logic / Domain Services",
            "repository", "Data Access / Persistence",
            "model", "Domain Entities / Data Models",
            "dto", "Data Transfer Objects (API Contracts)",
            "config", "Application Configuration",
            "util", "Cross-cutting Utilities",
            "security", "Authentication & Authorization",
            "exception", "Global Error Handling"
        );

        patternToRole.forEach((pattern, role) -> {
            paths.stream()
                .filter(p -> p.toLowerCase().contains("/" + pattern + "/") || p.toLowerCase().startsWith(pattern + "/"))
                .map(p -> p.contains("/") ? p.substring(0, p.lastIndexOf('/')) : p)
                .distinct()
                .forEach(pkg -> roles.add(new ComponentRole(pkg, role)));
        });

        return roles.stream().distinct().toList();
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

    public record ComponentRole(String path, String role) {}

    public record AnalysisResult(
            Stack stack,
            ArchitecturePattern pattern,
            int confidence,
            List<String> matchedSignals,
            List<String> packageSummary,
            List<String> suggestions,
            boolean needsLlmFallback,
            List<PatternScore> allScores,
            List<ComponentRole> inferredRoles) {
    }
}