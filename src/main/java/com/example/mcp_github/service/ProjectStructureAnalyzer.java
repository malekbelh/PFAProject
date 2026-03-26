package com.example.mcp_github.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;

/**
 * Rule-based project structure analyzer.
 *
 * Given a RepositorySnapshot (file tree + key file contents), it:
 * 1. Detects the tech stack (languages, frameworks, build tools)
 * 2. Detects the architecture pattern (MVC, Clean Architecture, etc.)
 * and assigns a confidence score (0–100)
 * 3. Extracts a package/module summary
 * 4. Produces improvement suggestions based on what's missing
 * 5. Signals whether LLM fallback is needed (confidence < threshold)
 */
@Service
public class ProjectStructureAnalyzer {

    private static final int CONFIDENCE_THRESHOLD = 70;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Main entry point. Analyzes the snapshot and returns a full AnalysisResult.
     */
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
        boolean needsLlm = best.score() < CONFIDENCE_THRESHOLD;

        return new AnalysisResult(
                stack,
                best.pattern(),
                best.score(),
                best.matchedSignals(),
                packageSummary,
                suggestions,
                needsLlm,
                scores);
    }

    // =========================================================================
    // STACK DETECTION
    // =========================================================================

    private Stack detectStack(List<String> paths, Map<String, String> files) {
        Set<String> languages = new HashSet<>();
        Set<String> frameworks = new HashSet<>();
        Set<String> buildTools = new HashSet<>();

        for (String path : paths) {
            // ── Languages from extensions ────────────────────────────────────
            if (path.endsWith(".java") || path.endsWith(".kt"))
                languages.add(path.endsWith(".kt") ? "Kotlin" : "Java");
            if (path.endsWith(".ts") || path.endsWith(".tsx"))
                languages.add("TypeScript");
            if (path.endsWith(".js") || path.endsWith(".jsx"))
                languages.add("JavaScript");
            if (path.endsWith(".py"))
                languages.add("Python");
            if (path.endsWith(".go"))
                languages.add("Go");
            if (path.endsWith(".rs"))
                languages.add("Rust");
            if (path.endsWith(".cs"))
                languages.add("C#");
            if (path.endsWith(".rb"))
                languages.add("Ruby");
            if (path.endsWith(".cpp") || path.endsWith(".cc"))
                languages.add("C++");

            // ── Build tools from config files ────────────────────────────────
            String fileName = fileName(path);
            if (fileName.equals("pom.xml"))
                buildTools.add("Maven");
            if (fileName.equals("build.gradle") || fileName.equals("build.gradle.kts"))
                buildTools.add("Gradle");
            if (fileName.equals("package.json"))
                buildTools.add("npm/yarn");
            if (fileName.equals("requirements.txt") || fileName.equals("setup.py") || fileName.equals("pyproject.toml"))
                buildTools.add("pip");
            if (fileName.equals("go.mod"))
                buildTools.add("Go modules");
            if (fileName.equals("Cargo.toml"))
                buildTools.add("Cargo");
            if (fileName.equals("Gemfile"))
                buildTools.add("Bundler");
        }

        // ── Frameworks from file contents ────────────────────────────────────
        String pomContent = files.getOrDefault("pom.xml", "");
        if (pomContent.contains("spring-boot"))
            frameworks.add("Spring Boot");
        if (pomContent.contains("spring-security"))
            frameworks.add("Spring Security");
        if (pomContent.contains("spring-data"))
            frameworks.add("Spring Data");
        if (pomContent.contains("quarkus"))
            frameworks.add("Quarkus");
        if (pomContent.contains("micronaut"))
            frameworks.add("Micronaut");

        String packageJson = files.getOrDefault("package.json", "");
        if (packageJson.contains("\"react\""))
            frameworks.add("React");
        if (packageJson.contains("\"next\""))
            frameworks.add("Next.js");
        if (packageJson.contains("\"vue\""))
            frameworks.add("Vue.js");
        if (packageJson.contains("\"angular\"") || packageJson.contains("\"@angular/core\""))
            frameworks.add("Angular");
        if (packageJson.contains("\"express\""))
            frameworks.add("Express");
        if (packageJson.contains("\"nestjs\"") || packageJson.contains("\"@nestjs/core\""))
            frameworks.add("NestJS");
        if (packageJson.contains("\"fastify\""))
            frameworks.add("Fastify");

        String requirements = files.getOrDefault("requirements.txt", "");
        if (requirements.contains("django"))
            frameworks.add("Django");
        if (requirements.contains("flask"))
            frameworks.add("Flask");
        if (requirements.contains("fastapi"))
            frameworks.add("FastAPI");

        return new Stack(
                languages.stream().sorted().toList(),
                frameworks.stream().sorted().toList(),
                buildTools.stream().sorted().toList());
    }

    // =========================================================================
    // PATTERN SCORING
    // =========================================================================

    private List<PatternScore> scorePatterns(List<String> paths) {
        List<PatternScore> scores = new ArrayList<>();

        scores.add(scoreMvc(paths));
        scores.add(scoreCleanArchitecture(paths));
        scores.add(scoreHexagonal(paths));
        scores.add(scoreLayered(paths));
        scores.add(scoreFeatureBased(paths));
        scores.add(scoreMvvm(paths));
        scores.add(scoreMicroservices(paths));

        return scores;
    }

    private PatternScore scoreMvc(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        // Spring-style (singular)
        if (hasFolder(paths, "controller") || hasFolder(paths, "controllers")) {
            signals.add("controller(s)/ folder");
            score += 30;
        }
        if (hasFolder(paths, "service") || hasFolder(paths, "services")) {
            signals.add("service(s)/ folder");
            score += 25;
        }
        if (hasFolder(paths, "model") || hasFolder(paths, "models") ||
                hasFolder(paths, "entity") || hasFolder(paths, "domain")) {
            signals.add("model/entity/ folder");
            score += 20;
        }
        if (hasFolder(paths, "repository") || hasFolder(paths, "repositories") || hasFolder(paths, "dao")) {
            signals.add("repository/dao/ folder");
            score += 15;
        }
        if (hasFolder(paths, "view") || hasFolder(paths, "views") ||
                hasFolder(paths, "templates")) {
            signals.add("view/templates/ folder");
            score += 10;
        }

        // Express-style MVC signals
        if (hasFolder(paths, "route") || hasFolder(paths, "routes")) {
            signals.add("routes/ folder (Express MVC)");
            score += 20;
        }
        if (hasFolder(paths, "middleware") || hasFolder(paths, "middlewares")) {
            signals.add("middleware(s)/ folder");
            score += 10;
        }

        return new PatternScore(ArchitecturePattern.MVC, Math.min(score, 100), signals);
    }

    // ── Clean Architecture (Uncle Bob) ───────────────────────────────────────
    private PatternScore scoreCleanArchitecture(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "domain")) {
            signals.add("domain/ layer");
            score += 30;
        }
        if (hasFolder(paths, "application") || hasFolder(paths, "usecase") || hasFolder(paths, "usecases")) {
            signals.add("application/usecase/ layer");
            score += 30;
        }
        if (hasFolder(paths, "infrastructure") || hasFolder(paths, "infra")) {
            signals.add("infrastructure/ layer");
            score += 25;
        }
        if (hasFolder(paths, "presentation") || hasFolder(paths, "adapter") || hasFolder(paths, "adapters")) {
            signals.add("presentation/adapter/ layer");
            score += 15;
        }

        return new PatternScore(ArchitecturePattern.CLEAN_ARCHITECTURE, Math.min(score, 100), signals);
    }

    // ── Hexagonal (Ports & Adapters) ─────────────────────────────────────────
    private PatternScore scoreHexagonal(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "port") || hasFolder(paths, "ports")) {
            signals.add("port/ folder");
            score += 35;
        }
        if (hasFolder(paths, "adapter") || hasFolder(paths, "adapters")) {
            signals.add("adapter/ folder");
            score += 35;
        }
        if (hasFolder(paths, "domain")) {
            signals.add("domain/ folder");
            score += 20;
        }
        if (hasFolder(paths, "application")) {
            signals.add("application/ folder");
            score += 10;
        }

        return new PatternScore(ArchitecturePattern.HEXAGONAL, Math.min(score, 100), signals);
    }

    // ── Layered (generic N-tier) ──────────────────────────────────────────────
    private PatternScore scoreLayered(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;
        int layers = 0;

        if (hasFolder(paths, "presentation") || hasFolder(paths, "web") || hasFolder(paths, "api")) {
            signals.add("presentation/web/ layer");
            layers++;
        }
        if (hasFolder(paths, "business") || hasFolder(paths, "logic") || hasFolder(paths, "service")) {
            signals.add("business/service/ layer");
            layers++;
        }
        if (hasFolder(paths, "data") || hasFolder(paths, "persistence") || hasFolder(paths, "repository")) {
            signals.add("data/persistence/ layer");
            layers++;
        }

        score = layers * 30;
        return new PatternScore(ArchitecturePattern.LAYERED, Math.min(score, 100), signals);
    }

    // ── Feature-based / Modular ───────────────────────────────────────────────
    private PatternScore scoreFeatureBased(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "feature") || hasFolder(paths, "features")) {
            signals.add("feature/ folder");
            score += 50;
        }
        if (hasFolder(paths, "module") || hasFolder(paths, "modules")) {
            signals.add("module/ folder");
            score += 40;
        }

        // Check if top-level folders themselves look like features
        // (each containing controller + service = feature module)
        long featureLikeFolders = paths.stream()
                .map(p -> p.contains("/") ? p.substring(0, p.indexOf('/')) : "")
                .distinct()
                .filter(f -> !f.isBlank())
                .filter(f -> {
                    boolean hasCtrl = paths.stream().anyMatch(p -> p.startsWith(f + "/") && p.contains("controller"));
                    boolean hasSvc = paths.stream().anyMatch(p -> p.startsWith(f + "/") && p.contains("service"));
                    return hasCtrl && hasSvc;
                })
                .count();

        if (featureLikeFolders >= 2) {
            signals.add(featureLikeFolders + " self-contained feature modules detected");
            score += 40;
        }

        return new PatternScore(ArchitecturePattern.FEATURE_BASED, Math.min(score, 100), signals);
    }

    // ── MVVM ─────────────────────────────────────────────────────────────────
    private PatternScore scoreMvvm(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "viewmodel") || hasFolder(paths, "viewmodels")) {
            signals.add("viewmodel/ folder");
            score += 60;
        }
        if (hasFolder(paths, "view") || hasFolder(paths, "views")) {
            signals.add("view/ folder");
            score += 25;
        }
        // Removed model/ — it's too generic and causes false positives with MVC
        if (hasFolder(paths, "model") || hasFolder(paths, "models")) {
            // Only add if viewmodel is already present, otherwise it's noise
            if (score > 0) {
                signals.add("model/ folder");
                score += 15;
            }
        }

        return new PatternScore(ArchitecturePattern.MVVM, Math.min(score, 100), signals);
    }

    // ── Microservices ─────────────────────────────────────────────────────────
    private PatternScore scoreMicroservices(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFile(paths, "docker-compose.yml") || hasFile(paths, "docker-compose.yaml")) {
            signals.add("docker-compose.yml");
            score += 25;
        }
        if (hasFile(paths, "Dockerfile")) {
            signals.add("Dockerfile");
            score += 20;
        }
        if (hasFolder(paths, "gateway") || hasFolder(paths, "api-gateway")) {
            signals.add("gateway/ service");
            score += 25;
        }
        if (hasFolder(paths, "discovery") || hasFolder(paths, "registry")) {
            signals.add("service discovery");
            score += 15;
        }

        // Multiple pom.xml / package.json = multi-module
        long buildFiles = paths.stream()
                .filter(p -> fileName(p).equals("pom.xml") || fileName(p).equals("package.json"))
                .count();
        if (buildFiles >= 3) {
            signals.add(buildFiles + " build files (multi-module)");
            score += 15;
        }

        return new PatternScore(ArchitecturePattern.MICROSERVICES, Math.min(score, 100), signals);
    }

    // =========================================================================
    // PACKAGE SUMMARY
    // =========================================================================

    /**
     * Extracts the top-level folder names (= packages/modules) from the file tree.
     * These become the nodes in the diagram.
     */
    private List<String> extractPackageSummary(List<String> paths) {
        Map<String, Integer> folderCounts = new HashMap<>();

        for (String path : paths) {
            String topFolder = path.contains("/")
                    ? path.substring(0, path.indexOf('/'))
                    : "(root)";
            folderCounts.merge(topFolder, 1, Integer::sum);
        }

        return folderCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> e.getKey() + "/ (" + e.getValue() + " files)")
                .toList();
    }

    // =========================================================================
    // SUGGESTIONS
    // =========================================================================

    private List<String> generateSuggestions(ArchitecturePattern pattern,
            List<String> paths, Stack stack) {
        List<String> suggestions = new ArrayList<>();

        switch (pattern) {
            case MVC -> {
                if (!hasFolder(paths, "exception") && !hasFolder(paths, "exceptions")
                        && !hasFile(paths, "GlobalExceptionHandler.java"))
                    suggestions.add(
                            "No global exception handler found — consider adding one to centralize error handling.");
                if (!hasFolder(paths, "dto") && !hasFolder(paths, "dtos"))
                    suggestions.add(
                            "No DTO layer detected — controllers may be exposing entities directly, which is a security risk.");
                if (!hasFolder(paths, "config") && !hasFolder(paths, "configuration"))
                    suggestions.add(
                            "No config/ package found — consider grouping Spring @Configuration classes together.");
                if (!hasFolder(paths, "test") && !hasFolder(paths, "tests"))
                    suggestions.add(
                            "No test folder detected — consider adding unit tests for service and controller layers.");
            }
            case CLEAN_ARCHITECTURE -> {
                if (!hasFolder(paths, "usecase") && !hasFolder(paths, "usecases") && !hasFolder(paths, "application"))
                    suggestions.add(
                            "No use-case layer found — Clean Architecture requires an explicit application/use-case layer.");
                if (!hasFolder(paths, "port") && !hasFolder(paths, "ports"))
                    suggestions.add(
                            "No ports defined — consider adding port interfaces to decouple domain from infrastructure.");
            }
            case HEXAGONAL -> {
                if (!hasFolder(paths, "domain"))
                    suggestions
                            .add("No domain/ core found — Hexagonal Architecture expects a pure domain at the center.");
                if (!hasFolder(paths, "port") && !hasFolder(paths, "ports"))
                    suggestions.add("No port interfaces found — ports are essential to Hexagonal Architecture.");
            }
            case FEATURE_BASED -> {
                suggestions.add(
                        "Ensure shared utilities are in a common/ or shared/ module to avoid duplication across features.");
                if (!hasFolder(paths, "common") && !hasFolder(paths, "shared"))
                    suggestions.add("No common/ or shared/ folder found — cross-feature code may be duplicated.");
            }
            case UNKNOWN -> {
                suggestions.add(
                        "Structure could not be confidently identified — consider reorganizing into a standard pattern (MVC, Clean Architecture, etc.).");
            }
            default -> {
            }
        }

        // General suggestions regardless of pattern
        if (stack.frameworks().contains("Spring Boot") && !hasFile(paths, "application.yml")
                && !hasFile(paths, "application.properties"))
            suggestions.add("No application.yml/properties found at root — make sure environment config is present.");

        if (!hasFile(paths, "README.md") && !hasFile(paths, "readme.md"))
            suggestions.add("No README.md found — consider adding one to document the project structure and setup.");

        return suggestions;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Returns true if any path contains a folder with this name as a segment. */
    private boolean hasFolder(List<String> paths, String folderName) {
        return paths.stream().anyMatch(p -> p.contains("/" + folderName + "/") ||
                p.startsWith(folderName + "/") ||
                p.contains("/" + folderName.toLowerCase() + "/") ||
                p.startsWith(folderName.toLowerCase() + "/"));
    }

    /** Returns true if any path ends with this exact file name. */
    private boolean hasFile(List<String> paths, String fileName) {
        return paths.stream().anyMatch(p -> p.equals(fileName) || p.endsWith("/" + fileName));
    }

    /** Extracts the file name from a path. */
    private String fileName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    // =========================================================================
    // RESULT MODELS
    // =========================================================================

    public enum ArchitecturePattern {
        MVC,
        CLEAN_ARCHITECTURE,
        HEXAGONAL,
        LAYERED,
        FEATURE_BASED,
        MVVM,
        MICROSERVICES,
        UNKNOWN
    }

    public record Stack(
            List<String> languages,
            List<String> frameworks,
            List<String> buildTools) {
    }

    public record PatternScore(
            ArchitecturePattern pattern,
            int score,
            List<String> matchedSignals) {
    }

    public record AnalysisResult(
            Stack stack,
            ArchitecturePattern pattern,
            int confidence,
            List<String> matchedSignals,
            List<String> packageSummary,
            List<String> suggestions,
            boolean needsLlmFallback,
            List<PatternScore> allScores) {
    }
}