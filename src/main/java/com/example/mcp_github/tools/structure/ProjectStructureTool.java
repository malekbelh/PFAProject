package com.example.mcp_github.tools.structure;

import com.example.mcp_github.service.GitHubFileTreeService;
import com.example.mcp_github.service.MemoryService;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.ProjectStructureAnalyzer;
import com.example.mcp_github.service.ProjectStructureAnalyzer.AnalysisResult;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ProjectStructureTool {

    private static final Logger logger = LoggerFactory.getLogger(ProjectStructureTool.class);

    private final GitHubFileTreeService gitHubFileTreeService;
    private final ProjectStructureAnalyzer analyzer;
    private final MemoryService memoryService;

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".py", ".go", ".rs", ".cs");

    public ProjectStructureTool(GitHubFileTreeService gitHubFileTreeService,
            ProjectStructureAnalyzer analyzer,
            MemoryService memoryService) {
        this.gitHubFileTreeService = gitHubFileTreeService;
        this.analyzer = analyzer;
        this.memoryService = memoryService;
    }

    @Tool(name = "analyzeProjectStructure", description = """
            ALWAYS use this tool when the user asks about project structure, architecture,
            design patterns, or how a repository is organized.

            Do NOT attempt to analyze code from context — this tool fetches live data
            directly from GitHub and produces accurate, up-to-date results.

            The repository can be inferred from memory (current_owner/current_repo)
            if the user does not specify one explicitly.

            This tool also generates a rollo.md documentation file in the current project directory.
            """)
    public String analyzeProjectStructure(
            @ToolParam(description = "GitHub repository in format 'owner/repo'. Can be omitted if a project is already initialized.", required = false) String repository,
            @ToolParam(description = "Branch name (default: inferred from memory or 'main')", required = false) String branch) {

        logger.info("🔧 TOOL CALLED: analyzeProjectStructure with repository={}", repository);

        try {
            // Infer from memory if not provided
            if (repository == null || repository.isBlank()) {
                String owner = memoryService.recall("current_owner");
                String repo = memoryService.recall("current_repo");
                if (owner == null || repo == null) {
                    return "ERROR: No repository specified and none found in memory. Please run initializeProject first.";
                }
                repository = owner + "/" + repo;
            }

            // Infer branch from memory if not provided
            if (branch == null || branch.isBlank()) {
                String rememberedBranch = memoryService.recall("current_branch");
                branch = (rememberedBranch != null && !rememberedBranch.isBlank()) ? rememberedBranch : "main";
            }

            String[] parts = repository.split("/");
            if (parts.length != 2) {
                return "ERROR: Invalid repository format. Please use 'owner/repo' format.";
            }

            String owner = parts[0];
            String repo = parts[1];

            // Fetch and analyze
            RepositorySnapshot snapshot = gitHubFileTreeService.snapshot(owner, repo, branch);

            if (snapshot.tree().isEmpty()) {
                return "ERROR: Could not fetch repository: %s/%s".formatted(owner, repo);
            }

            AnalysisResult analysis = analyzer.analyze(snapshot);

            // Generate both formats
            String chatResponse = buildChatResponse(analysis, snapshot);
            String fullDocumentation = generateFullDocumentation(analysis, snapshot);

            // Save to current project directory from memory
            String currentDir = memoryService.recall("current_path");
            if (currentDir == null || currentDir.isBlank()) {
                return chatResponse + "\n\n⚠️ **Could not save rollo.md** — no project path detected.\n" +
                        "Try running `initializeProject` first, or open a project in your workspace.";
            }

            Path rolloPath = Paths.get(currentDir, "rollo.md");

            Files.writeString(rolloPath, fullDocumentation, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            logger.info("✅ Saved rollo.md to: {}", rolloPath.toAbsolutePath());

            return chatResponse + "\n\n---\n\n" +
                    "✅ **Documentation saved!**\n\n" +
                    "📁 **File:** `%s`\n".formatted(rolloPath.toAbsolutePath()) +
                    "📄 **Size:** %d bytes\n".formatted(Files.size(rolloPath)) +
                    "💡 **Tip:** This file is now in your current project directory. You can:\n" +
                    "   • View it: Open `rollo.md` in your editor\n" +
                    "   • Commit it: `git add rollo.md && git commit -m \"Add architecture documentation\"`\n" +
                    "   • Share it with your team\n";

        } catch (Exception e) {
            logger.error("Error in analyzeProjectStructure", e);
            return "ERROR: Failed to analyze repository: " + e.getMessage();
        }
    }

    private String buildChatResponse(AnalysisResult analysis, RepositorySnapshot snapshot) {
        StringBuilder response = new StringBuilder();

        response.append("🔍 **PROJECT STRUCTURE ANALYSIS**\n");
        response.append("=".repeat(50)).append("\n\n");

        response.append("**Repository:** `%s/%s`\n".formatted(snapshot.owner(), snapshot.repo()));
        response.append("**Branch:** `%s`\n".formatted(snapshot.branch()));
        response.append("**Files analyzed:** %d\n\n".formatted(snapshot.tree().size()));

        // Tech Stack
        response.append("## 📦 Technology Stack\n\n");
        Stack stack = analysis.stack();
        if (!stack.languages().isEmpty()) {
            response.append("**Languages:** %s\n".formatted(String.join(", ", stack.languages())));
        }
        if (!stack.frameworks().isEmpty()) {
            response.append("**Frameworks:** %s\n".formatted(String.join(", ", stack.frameworks())));
        }
        if (!stack.buildTools().isEmpty()) {
            response.append("**Build Tools:** %s\n".formatted(String.join(", ", stack.buildTools())));
        }
        response.append("\n");

        // Architecture Pattern
        response.append("## 🏗️ Architecture Pattern\n\n");
        response.append("**Detected:** %s\n".formatted(formatPatternName(analysis.pattern())));
        response.append("**Confidence:** %d%%\n\n".formatted(analysis.confidence()));

        if (!analysis.matchedSignals().isEmpty()) {
            response.append("**Evidence:**\n");
            for (String signal : analysis.matchedSignals()) {
                response.append("  ✓ %s\n".formatted(signal));
            }
            response.append("\n");
        }

        // Quick Diagram (simplified for chat)
        response.append("## 📊 Architecture Overview\n\n");
        response.append("```\n");
        response.append(generateAsciiOverview(analysis));
        response.append("```\n\n");

        // Key Recommendations
        response.append("## 💡 Key Recommendations\n\n");
        List<String> suggestions = analysis.suggestions();
        if (suggestions.isEmpty()) {
            response.append("✓ The architecture is well-structured!\n");
        } else {
            // Show top 3 recommendations in chat
            suggestions.stream().limit(3).forEach(s -> response.append("  • %s\n".formatted(s)));
            if (suggestions.size() > 3) {
                response.append("  • *...and %d more (see rollo.md for details)*\n".formatted(suggestions.size() - 3));
            }
        }

        return response.toString();
    }

    private String generateFullDocumentation(AnalysisResult analysis, RepositorySnapshot snapshot) {
        StringBuilder doc = new StringBuilder();

        // Header
        doc.append("# 🏗️ Project Architecture Documentation\n\n");
        doc.append("**Repository:** `%s/%s`\n".formatted(snapshot.owner(), snapshot.repo()));
        doc.append("**Branch:** `%s`\n".formatted(snapshot.branch()));
        doc.append("**Generated:** `%s`\n\n".formatted(new Date()));
        doc.append("---\n\n");

        // Executive Summary
        doc.append("## 📋 Executive Summary\n\n");
        doc.append("This document provides a comprehensive analysis of the project's architecture, ");
        doc.append("technology stack, and structural patterns.\n\n");

        // Technology Stack
        doc.append("## 🔧 Technology Stack\n\n");
        Stack stack = analysis.stack();

        doc.append("| Category | Technology |\n");
        doc.append("|----------|------------|\n");
        if (!stack.languages().isEmpty()) {
            doc.append("| **Languages** | %s |\n".formatted(String.join(", ", stack.languages())));
        }
        if (!stack.frameworks().isEmpty()) {
            doc.append("| **Frameworks** | %s |\n".formatted(String.join(", ", stack.frameworks())));
        }
        if (!stack.buildTools().isEmpty()) {
            doc.append("| **Build Tools** | %s |\n".formatted(String.join(", ", stack.buildTools())));
        }
        doc.append("\n---\n\n");

        // Architecture Pattern
        doc.append("## 🏗️ Architecture Pattern: %s\n\n".formatted(formatPatternName(analysis.pattern())));
        doc.append("**Confidence Level:** %d%%\n\n".formatted(analysis.confidence()));

        if (!analysis.matchedSignals().isEmpty()) {
            doc.append("### Key Indicators\n\n");
            for (String signal : analysis.matchedSignals()) {
                doc.append("- ✓ %s\n".formatted(signal));
            }
            doc.append("\n");
        }

        doc.append("### Layer Description\n\n");
        doc.append(generateLayerDescription(analysis));
        doc.append("\n---\n\n");

        // Mermaid Diagram
        doc.append("## 📊 Component Diagram\n\n");
        doc.append("```mermaid\n");
        doc.append(generateMermaidDiagram(snapshot, analysis));
        doc.append("```\n\n");
        doc.append("*Diagram can be viewed at [mermaid.live](https://mermaid.live) for better visualization.*\n\n");
        doc.append("---\n\n");

        // Project Structure
        doc.append("## 📁 Project Structure\n\n");
        doc.append("```\n");
        generateDirectoryTree(snapshot, doc);
        doc.append("```\n\n");
        doc.append("---\n\n");

        // Component Breakdown
        doc.append("## 🧩 Component Breakdown\n\n");
        generateComponentBreakdown(snapshot, analysis, doc);
        doc.append("---\n\n");

        // Data Flow
        doc.append("## 🔄 Data Flow\n\n");
        doc.append("```\n");
        doc.append(generateDataFlowDescription(analysis));
        doc.append("```\n\n");
        doc.append("---\n\n");

        // Full Recommendations
        doc.append("## 💡 Recommendations\n\n");
        List<String> suggestions = analysis.suggestions();
        if (suggestions.isEmpty()) {
            doc.append("✓ The architecture is well-structured! Consider:\n\n");
            doc.append("- Adding comprehensive API documentation\n");
            doc.append("- Implementing automated testing\n");
            doc.append("- Setting up CI/CD pipelines\n");
            doc.append("- Adding monitoring and observability\n");
        } else {
            for (String suggestion : suggestions) {
                doc.append("- %s\n".formatted(suggestion));
            }
        }
        doc.append("\n\n---\n\n");

        // Footer
        doc.append("## 📝 About This Document\n\n");
        doc.append("This document was automatically generated using the GitHub MCP Server. ");
        doc.append("To update this documentation, simply ask for the project structure again.\n\n");
        doc.append("*Last updated: %s*\n".formatted(new Date()));

        return doc.toString();
    }

    private String generateAsciiOverview(AnalysisResult analysis) {
        return switch (analysis.pattern()) {
            case MVC -> """
                    ┌─────────────────┐
                    │   Controllers   │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │    Services     │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  Repositories   │
                    └─────────────────┘
                    """;
            case LAYERED -> """
                    ┌─────────────────┐
                    │  Presentation   │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   Business      │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │     Data        │
                    └─────────────────┘
                    """;
            default -> """
                    Client → [API Layer] → [Business Logic] → [Data Access] → Database
                    """;
        };
    }

    private String generateLayerDescription(AnalysisResult analysis) {
        return switch (analysis.pattern()) {
            case MVC -> """
                    - **Controller Layer**: Handles HTTP requests, input validation, and response formatting
                    - **Service Layer**: Contains business logic and orchestration
                    - **Repository Layer**: Manages data access and persistence
                    - **Model Layer**: Defines data structures and domain entities
                    """;
            case CLEAN_ARCHITECTURE -> """
                    - **Entities**: Core business objects and enterprise-wide rules
                    - **Use Cases**: Application-specific business rules
                    - **Interface Adapters**: Convert data between use cases and external agencies
                    - **Frameworks & Drivers**: External tools like databases, web frameworks
                    """;
            default -> """
                    - **Presentation Layer**: Handles user interaction and API endpoints
                    - **Business Layer**: Core application logic
                    - **Data Layer**: Database access and external integrations
                    """;
        };
    }

    private String generateMermaidDiagram(RepositorySnapshot snapshot, AnalysisResult analysis) {
        StringBuilder mermaid = new StringBuilder();
        mermaid.append("graph TD\n");

        switch (analysis.pattern()) {
            case MVC:
                List<String> controllers = findFilesInFolders(snapshot, List.of("controller", "controllers"));
                List<String> services = findFilesInFolders(snapshot, List.of("service", "services"));
                List<String> repositories = findFilesInFolders(snapshot, List.of("repository", "repositories", "dao"));

                appendSubgraph(mermaid, "Controllers", "CTRL", controllers);
                appendSubgraph(mermaid, "Services", "SVC", services);
                appendSubgraph(mermaid, "Repositories", "REPO", repositories);

                mermaid.append("    Controllers --> Services\n");
                mermaid.append("    Services --> Repositories\n");
                break;

            case LAYERED:
                List<String> presentation = findFilesInFolders(snapshot,
                        List.of("controller", "controllers", "api", "web"));
                List<String> business = findFilesInFolders(snapshot, List.of("service", "services", "business"));
                List<String> data = findFilesInFolders(snapshot,
                        List.of("repository", "repositories", "model", "models", "entity"));

                appendSubgraph(mermaid, "Presentation", "PRES", presentation);
                appendSubgraph(mermaid, "Business", "BUS", business);
                appendSubgraph(mermaid, "Data", "DAT", data);

                mermaid.append("    Presentation --> Business\n");
                mermaid.append("    Business --> Data\n");
                break;

            default:
                mermaid.append("    Client([\"Client\"])\n");
                mermaid.append("    API[\"API Layer\"]\n");
                mermaid.append("    Logic[\"Business Logic\"]\n");
                mermaid.append("    Database[(\"Database\")]\n");
                mermaid.append("    Client --> API\n");
                mermaid.append("    API --> Logic\n");
                mermaid.append("    Logic --> Database\n");
        }

        return mermaid.toString();
    }

    // Returns up to 8 class names found in any of the given folder names
    private List<String> findFilesInFolders(RepositorySnapshot snapshot, List<String> folderNames) {
        List<String> found = new ArrayList<>();
        for (String path : snapshot.allPaths()) {
            if (found.size() >= 8)
                break;
            if (!isSourceFile(path))
                continue;
            String lowerPath = path.toLowerCase();
            for (String folder : folderNames) {
                String lowerFolder = folder.toLowerCase();
                // Match both top-level (controllers/Foo.js) and nested (/controllers/Foo.js)
                if (lowerPath.startsWith(lowerFolder + "/") ||
                        lowerPath.contains("/" + lowerFolder + "/")) {
                    String className = extractClassName(path);
                    if (className != null && !found.contains(className)) {
                        found.add(className);
                    }
                    break;
                }
            }
        }
        return found;
    }

    // Appends a valid Mermaid subgraph with proper node syntax
    private void appendSubgraph(StringBuilder mermaid, String label, String prefix, List<String> classNames) {
        mermaid.append("    subgraph %s[\"%s\"]\n".formatted(escapeMermaidName(label), label));
        if (classNames.isEmpty()) {
            // Valid Mermaid node: id["Label"]
            mermaid.append("        %s_empty[\"No components found\"]\n".formatted(prefix));
        } else {
            for (int i = 0; i < classNames.size(); i++) {
                String className = classNames.get(i);
                String nodeId = prefix + "_" + escapeMermaidName(className);
                mermaid.append("        %s[\"%s\"]\n".formatted(nodeId, className));
            }
        }
        mermaid.append("    end\n\n");
    }

    private void generateDirectoryTree(RepositorySnapshot snapshot, StringBuilder doc) {
        TreeSet<String> sortedPaths = new TreeSet<>(snapshot.allPaths());

        // Show top-level structure first
        Set<String> topLevelDirs = new TreeSet<>();
        for (String path : sortedPaths) {
            if (path.contains("/")) {
                topLevelDirs.add(path.substring(0, path.indexOf('/')));
            }
        }

        for (String dir : topLevelDirs.stream().limit(15).collect(Collectors.toList())) {
            doc.append("%s/\n".formatted(dir));
            // Show a few files from each directory
            snapshot.allPaths().stream()
                    .filter(p -> p.startsWith(dir + "/") && isSourceFile(p))
                    .limit(5)
                    .forEach(p -> {
                        String fileName = p.substring(p.lastIndexOf('/') + 1);
                        doc.append("  ├── %s\n".formatted(fileName));
                    });
        }

        if (snapshot.allPaths().size() > 100) {
            doc.append("\n... and %d more files\n".formatted(snapshot.allPaths().size() - 100));
        }
    }

    private void generateComponentBreakdown(RepositorySnapshot snapshot, AnalysisResult analysis, StringBuilder doc) {
        Map<String, List<String>> components = new HashMap<>();

        for (String path : snapshot.allPaths()) {
            if (isSourceFile(path)) {
                String className = extractClassName(path);
                if (className != null) {
                    String type = "Other";
                    if (path.contains("controller"))
                        type = "Controllers";
                    else if (path.contains("service"))
                        type = "Services";
                    else if (path.contains("repository") || path.contains("dao"))
                        type = "Repositories";
                    else if (path.contains("model") || path.contains("entity"))
                        type = "Models";
                    else if (path.contains("middleware"))
                        type = "Middleware";
                    else if (path.contains("util"))
                        type = "Utilities";

                    components.computeIfAbsent(type, k -> new ArrayList<>()).add(className);
                }
            }
        }

        for (Map.Entry<String, List<String>> entry : components.entrySet()) {
            doc.append("### %s\n\n".formatted(entry.getKey()));
            for (String component : entry.getValue().stream().limit(10).collect(Collectors.toList())) {
                doc.append("- `%s`\n".formatted(component));
            }
            if (entry.getValue().size() > 10) {
                doc.append("- *...and %d more*\n".formatted(entry.getValue().size() - 10));
            }
            doc.append("\n");
        }
    }

    private String generateDataFlowDescription(AnalysisResult analysis) {
        return switch (analysis.pattern()) {
            case MVC -> """
                    Client Request
                         ↓
                    Controller (Receives request, validates input)
                         ↓
                    Service (Business logic execution)
                         ↓
                    Repository (Data access)
                         ↓
                    Database
                         ↓
                    Response flows back through the same layers
                    """;
            case LAYERED -> """
                    Request → Presentation Layer → Business Layer → Data Layer → Response
                    """;
            default -> """
                    Request → Processing → Business Logic → Data Access → Response
                    """;
        };
    }

    private String formatPatternName(ArchitecturePattern pattern) {
        return switch (pattern) {
            case MVC -> "MVC (Model-View-Controller)";
            case CLEAN_ARCHITECTURE -> "Clean Architecture";
            case HEXAGONAL -> "Hexagonal Architecture (Ports & Adapters)";
            case LAYERED -> "Layered Architecture";
            case FEATURE_BASED -> "Feature-Based / Modular Architecture";
            case MVVM -> "MVVM (Model-View-ViewModel)";
            case MICROSERVICES -> "Microservices Architecture";
            default -> "Unknown Pattern";
        };
    }

    private boolean isSourceFile(String path) {
        return SOURCE_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private String extractClassName(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        for (String ext : SOURCE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return fileName.substring(0, fileName.length() - ext.length());
            }
        }
        return null;
    }

    private Set<String> extractDependencies(String content, String currentClass) {
        Set<String> dependencies = new HashSet<>();

        Pattern javaImportPattern = Pattern.compile("import\\s+([^;]+);");
        var matcher = javaImportPattern.matcher(content);
        while (matcher.find()) {
            String importPath = matcher.group(1);
            String className = importPath.substring(importPath.lastIndexOf('.') + 1);
            if (!className.equals(currentClass) && !className.equals("*")) {
                dependencies.add(className);
            }
        }

        Pattern tsImportPattern = Pattern.compile("import\\s+.*\\s+from\\s+['\"]([^'\"]+)['\"]");
        matcher = tsImportPattern.matcher(content);
        while (matcher.find()) {
            String importPath = matcher.group(1);
            if (importPath.startsWith(".")) {
                String className = importPath.substring(importPath.lastIndexOf('/') + 1);
                className = className.replaceAll("\\.(js|ts|jsx|tsx)$", "");
                if (!className.equals(currentClass)) {
                    dependencies.add(className);
                }
            }
        }

        return dependencies;
    }

    private String escapeMermaidName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
