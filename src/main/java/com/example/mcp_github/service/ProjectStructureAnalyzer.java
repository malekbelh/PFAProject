package com.example.mcp_github.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;

@Service
public class ProjectStructureAnalyzer {

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

    private Stack detectStack(List<String> paths, Map<String, String> files) {
        Set<String> languages = new HashSet<>();
        Set<String> frameworks = new HashSet<>();
        Set<String> buildTools = new HashSet<>();

        for (String path : paths) {
            String lower = path.toLowerCase();

            // Languages from extensions
            if (lower.endsWith(".java"))
                languages.add("Java");
            if (lower.endsWith(".kt"))
                languages.add("Kotlin");
            if (lower.endsWith(".ts") || lower.endsWith(".tsx"))
                languages.add("TypeScript");
            if (lower.endsWith(".js") || lower.endsWith(".jsx"))
                languages.add("JavaScript");
            if (lower.endsWith(".py"))
                languages.add("Python");
            if (lower.endsWith(".go"))
                languages.add("Go");
            if (lower.endsWith(".rs"))
                languages.add("Rust");
            if (lower.endsWith(".cs"))
                languages.add("C#");
            if (lower.endsWith(".rb"))
                languages.add("Ruby");
            if (lower.endsWith(".cpp") || lower.endsWith(".cc"))
                languages.add("C++");

            // Build tools from config files
            String fileName = fileName(lower);
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
            if (fileName.equals("cargo.toml"))
                buildTools.add("Cargo");
            if (fileName.equals("gemfile"))
                buildTools.add("Bundler");
            if (fileName.endsWith(".csproj") || fileName.endsWith(".sln"))
                buildTools.add(".NET");
        }

        // Frameworks from file contents
        String pomContent = files.getOrDefault("pom.xml", "").toLowerCase();
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

        String packageJson = files.getOrDefault("package.json", "").toLowerCase();
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
        if (packageJson.contains("\"@nestjs/core\""))
            frameworks.add("NestJS");
        if (packageJson.contains("\"fastify\""))
            frameworks.add("Fastify");

        String requirements = files.getOrDefault("requirements.txt", "").toLowerCase();
        if (requirements.contains("django"))
            frameworks.add("Django");
        if (requirements.contains("flask"))
            frameworks.add("Flask");
        if (requirements.contains("fastapi"))
            frameworks.add("FastAPI");

        // Detect ASP.NET from .csproj contents
        files.forEach((filePath, content) -> {
            if (filePath.toLowerCase().endsWith(".csproj")) {
                String c = content.toLowerCase();
                if (c.contains("microsoft.aspnetcore"))
                    frameworks.add("ASP.NET Core");
                else if (c.contains("microsoft.aspnet"))
                    frameworks.add("ASP.NET MVC");
            }
        });

        return new Stack(
                languages.stream().sorted().toList(),
                frameworks.stream().sorted().toList(),
                buildTools.stream().sorted().toList());
    }

    // PATTERN SCORING

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

    // MVC
    private PatternScore scoreMvc(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "controllers") || hasFolder(paths, "controller")) {
            signals.add("Controllers/ folder");
            score += 30;
        }
        if (hasFolder(paths, "services") || hasFolder(paths, "service")) {
            signals.add("Services/ folder");
            score += 25;
        }
        if (hasFolder(paths, "models") || hasFolder(paths, "model")
                || hasFolder(paths, "entities") || hasFolder(paths, "entity")
                || hasFolder(paths, "domain")) {
            signals.add("Models/Entities/ folder");
            score += 20;
        }
        if (hasFolder(paths, "repositories") || hasFolder(paths, "repository")
                || hasFolder(paths, "dao")) {
            signals.add("Repositories/ folder");
            score += 15;
        }
        if (hasFolder(paths, "views") || hasFolder(paths, "view")
                || hasFolder(paths, "templates")) {
            signals.add("Views/Templates/ folder");
            score += 10;
        }

        return new PatternScore(ArchitecturePattern.MVC, Math.min(score, 100), signals);
    }

    // Clean Architecture
    private PatternScore scoreCleanArchitecture(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "domain")) {
            signals.add("Domain/ layer");
            score += 30;
        }
        if (hasFolder(paths, "application") || hasFolder(paths, "usecase")
                || hasFolder(paths, "usecases") || hasFolder(paths, "use-cases")) {
            signals.add("Application/UseCase/ layer");
            score += 30;
        }
        if (hasFolder(paths, "infrastructure") || hasFolder(paths, "infra")) {
            signals.add("Infrastructure/ layer");
            score += 25;
        }
        if (hasFolder(paths, "presentation") || hasFolder(paths, "adapter")
                || hasFolder(paths, "adapters")) {
            signals.add("Presentation/Adapter/ layer");
            score += 15;
        }

        return new PatternScore(ArchitecturePattern.CLEAN_ARCHITECTURE, Math.min(score, 100), signals);
    }

    // ── Hexagonal
    private PatternScore scoreHexagonal(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "port") || hasFolder(paths, "ports")) {
            signals.add("Ports/ folder");
            score += 35;
        }
        if (hasFolder(paths, "adapter") || hasFolder(paths, "adapters")) {
            signals.add("Adapters/ folder");
            score += 35;
        }
        if (hasFolder(paths, "domain")) {
            signals.add("Domain/ folder");
            score += 20;
        }
        if (hasFolder(paths, "application")) {
            signals.add("Application/ folder");
            score += 10;
        }

        return new PatternScore(ArchitecturePattern.HEXAGONAL, Math.min(score, 100), signals);
    }

    // Layered
    private PatternScore scoreLayered(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;
        int layers = 0;

        if (hasFolder(paths, "presentation") || hasFolder(paths, "web")
                || hasFolder(paths, "api")) {
            signals.add("Presentation/Web/ layer");
            layers++;
        }
        if (hasFolder(paths, "business") || hasFolder(paths, "logic")
                || hasFolder(paths, "service") || hasFolder(paths, "services")) {
            signals.add("Business/Service/ layer");
            layers++;
        }
        if (hasFolder(paths, "data") || hasFolder(paths, "persistence")
                || hasFolder(paths, "repository") || hasFolder(paths, "repositories")) {
            signals.add("Data/Persistence/ layer");
            layers++;
        }

        score = layers * 30;
        return new PatternScore(ArchitecturePattern.LAYERED, Math.min(score, 100), signals);
    }

    // Feature-based
    private PatternScore scoreFeatureBased(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "feature") || hasFolder(paths, "features")) {
            signals.add("Features/ folder");
            score += 50;
        }
        if (hasFolder(paths, "module") || hasFolder(paths, "modules")) {
            signals.add("Modules/ folder");
            score += 40;
        }

        long featureLikeFolders = paths.stream()
                .map(p -> p.contains("/") ? p.substring(0, p.indexOf('/')) : "")
                .distinct()
                .filter(f -> !f.isBlank())
                .filter(f -> {
                    String fLower = f.toLowerCase();
                    boolean hasCtrl = paths.stream().anyMatch(
                            p -> p.toLowerCase().startsWith(fLower + "/") && p.toLowerCase().contains("controller"));
                    boolean hasSvc = paths.stream().anyMatch(
                            p -> p.toLowerCase().startsWith(fLower + "/") && p.toLowerCase().contains("service"));
                    return hasCtrl && hasSvc;
                })
                .count();

        if (featureLikeFolders >= 2) {
            signals.add(featureLikeFolders + " self-contained feature modules detected");
            score += 40;
        }

        return new PatternScore(ArchitecturePattern.FEATURE_BASED, Math.min(score, 100), signals);
    }

    // MVVM
    private PatternScore scoreMvvm(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "viewmodel") || hasFolder(paths, "viewmodels")) {
            signals.add("ViewModels/ folder");
            score += 50;
        }
        if (hasFolder(paths, "view") || hasFolder(paths, "views")) {
            signals.add("Views/ folder");
            score += 25;
        }
        if (hasFolder(paths, "model") || hasFolder(paths, "models")) {
            signals.add("Models/ folder");
            score += 25;
        }

        return new PatternScore(ArchitecturePattern.MVVM, Math.min(score, 100), signals);
    }

    // Microservices
    private PatternScore scoreMicroservices(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFile(paths, "docker-compose.yml") || hasFile(paths, "docker-compose.yaml")) {
            signals.add("docker-compose.yml");
            score += 25;
        }
        if (hasFile(paths, "dockerfile")) {
            signals.add("Dockerfile");
            score += 20;
        }
        if (hasFolder(paths, "gateway") || hasFolder(paths, "api-gateway")) {
            signals.add("Gateway/ service");
            score += 25;
        }
        if (hasFolder(paths, "discovery") || hasFolder(paths, "registry")) {
            signals.add("Service discovery");
            score += 15;
        }

        long buildFiles = paths.stream()
                .filter(p -> {
                    String f = fileName(p.toLowerCase());
                    return f.equals("pom.xml") || f.equals("package.json");
                })
                .count();
        if (buildFiles >= 3) {
            signals.add(buildFiles + " build files (multi-module)");
            score += 15;
        }

        return new PatternScore(ArchitecturePattern.MICROSERVICES, Math.min(score, 100), signals);
    }

    // PACKAGE SUMMARY

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

    // SUGGESTIONS

    private List<String> generateSuggestions(ArchitecturePattern pattern,
            List<String> paths, Stack stack) {
        List<String> suggestions = new ArrayList<>();

        switch (pattern) {
            case MVC -> {
                if (!hasFolder(paths, "exception") && !hasFolder(paths, "exceptions")
                        && !hasFile(paths, "globalexceptionhandler.java"))
                    suggestions.add(
                            "No global exception handler found — consider adding one to centralize error handling.");
                if (!hasFolder(paths, "dto") && !hasFolder(paths, "dtos"))
                    suggestions.add(
                            "No DTO layer detected — controllers may be exposing entities directly, which is a security risk.");
                if (!hasFolder(paths, "config") && !hasFolder(paths, "configuration"))
                    suggestions.add("No config/ package found — consider grouping configuration classes together.");
                if (!hasFolder(paths, "test") && !hasFolder(paths, "tests"))
                    suggestions.add(
                            "No test folder detected — consider adding unit tests for service and controller layers.");
            }
            case CLEAN_ARCHITECTURE -> {
                if (!hasFolder(paths, "usecase") && !hasFolder(paths, "usecases")
                        && !hasFolder(paths, "application"))
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

        // Universal suggestions
        if (stack.frameworks().contains("Spring Boot")
                && !hasFile(paths, "application.yml")
                && !hasFile(paths, "application.properties"))
            suggestions.add("No application.yml/properties found at root — make sure environment config is present.");

        if (!hasFile(paths, "readme.md"))
            suggestions.add("No README.md found — consider adding one to document the project structure and setup.");

        return suggestions;
    }

    // HELPERS

    /**
     * Case-insensitive folder detection.
     * Matches any path segment equal to folderName, regardless of casing.
     */
    private boolean hasFolder(List<String> paths, String folderName) {
        String lower = folderName.toLowerCase();
        return paths.stream().anyMatch(p -> {
            String lp = p.toLowerCase();
            return lp.contains("/" + lower + "/")
                    || lp.startsWith(lower + "/");
        });
    }

    /**
     * Case-insensitive file detection.
     */
    private boolean hasFile(List<String> paths, String fileName) {
        String lower = fileName.toLowerCase();
        return paths.stream().anyMatch(p -> {
            String lp = p.toLowerCase();
            return lp.equals(lower) || lp.endsWith("/" + lower);
        });
    }

    private String fileName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    // RESULT MODELS

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