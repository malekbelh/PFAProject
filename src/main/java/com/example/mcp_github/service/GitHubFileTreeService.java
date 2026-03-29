package com.example.mcp_github.service;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Service for fetching the file tree and key file contents from a GitHub
 * repository.
 * Used as the data layer for project structure analysis.
 */

@Service
public class GitHubFileTreeService {

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", // JVM
            ".ts", ".tsx", ".js", ".jsx", // JS/TS
            ".py", // Python
            ".go", // Go
            ".rs", // Rust
            ".cs", // C#
            ".cpp", ".cc", ".h", ".hpp", // C/C++
            ".rb" // Ruby
    );
    private static final Set<String> ENTRY_POINT_NAMES = Set.of(
            "main", "app", "application", "index",
            "server", "bootstrap", "program", "startup");

    private static final Set<String> CONFIG_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "package.json", "requirements.txt", "setup.py", "pyproject.toml",
            "go.mod", "Cargo.toml", "*.csproj", "Gemfile");

    // ── Paths to always skip ─────────────────────────────────────────────────
    private static final Set<String> IGNORED_PREFIXES = Set.of(
            "node_modules/", ".git/", "dist/", "build/", "target/",
            ".mvn/", "__pycache__/", ".venv/", "venv/", "vendor/",
            "coverage/", ".idea/", ".vscode/");

    private static final int MAX_SOURCE_FILES = 25;

    // ── Buffer size for large responses (10MB should be enough for most repos) ──
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10MB

    private final WebClient webClient;

    public GitHubFileTreeService(
            @Value("${github.api.base-url}") String baseUrl,
            @Value("${github.api.token:}") String token) {

        HttpClient httpClient = HttpClient.create(
                ConnectionProvider.builder("github")
                        .maxConnections(100)
                        .build())
                .responseTimeout(java.time.Duration.ofSeconds(30))
                .followRedirect(true);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE));

        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }

        this.webClient = builder.build();
    }

    /**
     * Fetches the full recursive file tree of a repository.
     * Returns a flat list of every file path (blobs only, no directories).
     */
    public List<TreeEntry> getFileTree(String owner, String repo, String branch) {
        String resolvedBranch = (branch != null && !branch.isBlank()) ? branch : "main";

        try {
            GitTreeResponse response = webClient.get()
                    .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1",
                            owner, repo, resolvedBranch)
                    .retrieve()
                    .bodyToMono(GitTreeResponse.class)
                    .block();

            if (response == null || response.tree() == null) {
                return List.of();
            }

            // Keep only files (blobs), skip directory entries and ignored paths
            return response.tree().stream()
                    .filter(e -> "blob".equals(e.type()))
                    .filter(e -> !isIgnored(e.path()))
                    .toList();

        } catch (Exception e) {
            // Log the error and return empty list
            System.err.println("Error fetching file tree: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * From the file tree, selects and fetches the content of:
     * 1. All config/manifest files (pom.xml, package.json, etc.)
     * 2. Entry-point source files (main, app, index, etc.)
     * 3. A sample of remaining source files (up to MAX_SOURCE_FILES total)
     *
     * Returns a map of { filePath → decoded file content }.
     */

    public Map<String, String> fetchKeyFiles(String owner, String repo,
            String branch, List<TreeEntry> tree) {

        List<String> paths = tree.stream().map(TreeEntry::path).toList();

        // ── Priority 1: config files
        List<String> toFetch = paths.stream()
                .filter(this::isConfigFile)
                .toList();

        // ── Priority 2: entry-point source files
        List<String> entryPoints = paths.stream()
                .filter(p -> !isConfigFile(p))
                .filter(this::isSourceFile)
                .filter(this::isEntryPoint)
                .toList();

        // ── Priority 3: remaining source files
        List<String> remaining = paths.stream()
                .filter(p -> !isConfigFile(p))
                .filter(this::isSourceFile)
                .filter(p -> !isEntryPoint(p))
                .limit(Math.max(0, MAX_SOURCE_FILES - toFetch.size() - entryPoints.size()))
                .toList();

        List<String> selected = new java.util.ArrayList<>();
        selected.addAll(toFetch);
        selected.addAll(entryPoints);
        selected.addAll(remaining);

        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String path : selected) {
            try {
                String content = fetchFileContent(owner, repo, branch, path);
                if (content != null) {
                    result.put(path, content);
                }
            } catch (Exception e) {
                // Skip files that fail (binary, too large, etc.)
            }
        }

        return result;
    }

    /**
     * Convenience method: fetches tree + key files in one call.
     */
    public RepositorySnapshot snapshot(String owner, String repo, String branch) {
        List<TreeEntry> tree = getFileTree(owner, repo, branch);
        Map<String, String> keyFiles = fetchKeyFiles(owner, repo, branch, tree);
        return new RepositorySnapshot(owner, repo, branch, tree, keyFiles);
    }

    /**
     * Fetches and decodes the content of a single file.
     * GitHub returns file content as base64 with newlines — we strip and decode.
     */
    private String fetchFileContent(String owner, String repo, String branch, String path) {
        try {
            FileContentResponse response = webClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}",
                            owner, repo, path, branch)
                    .retrieve()
                    .bodyToMono(FileContentResponse.class)
                    .block();

            if (response == null || response.content() == null) {
                return null;
            }

            // GitHub encodes content as base64 with \n line breaks
            String cleaned = response.content().replace("\n", "").trim();
            return new String(Base64.getDecoder().decode(cleaned));

        } catch (WebClientResponseException.NotFound e) {
            return null; // File doesn't exist on this branch
        } catch (Exception e) {
            // Handle other errors (timeout, etc.)
            return null;
        }
    }

    private boolean isIgnored(String path) {
        for (String prefix : IGNORED_PREFIXES) {
            if (path.startsWith(prefix))
                return true;
        }
        return false;
    }

    private boolean isSourceFile(String path) {
        for (String ext : SOURCE_EXTENSIONS) {
            if (path.endsWith(ext))
                return true;
        }
        return false;
    }

    private boolean isConfigFile(String path) {
        String fileName = path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1)
                : path;
        return CONFIG_FILES.contains(fileName);
    }

    private boolean isEntryPoint(String path) {
        String fileName = path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1)
                : path;
        // Strip extension and check against entry point names
        String nameOnly = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
        return ENTRY_POINT_NAMES.contains(nameOnly.toLowerCase());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitTreeResponse(
            String sha,
            List<TreeEntry> tree,
            boolean truncated) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TreeEntry(
            String path,
            String type, // "blob" = file, "tree" = directory
            String sha,
            @JsonProperty("size") Long size) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileContentResponse(
            String name,
            String path,
            String content, // base64 encoded
            String encoding,
            String sha) {
    }

    /**
     * Snapshot of a repository at a given point in time:
     * the full file tree + the content of selected key files.
     */
    public record RepositorySnapshot(
            String owner,
            String repo,
            String branch,
            List<TreeEntry> tree, // all file paths
            Map<String, String> keyFiles // path → decoded content
    ) {
        /** All file paths as a plain list of strings — useful for pattern matching. */
        public List<String> allPaths() {
            return tree.stream().map(TreeEntry::path).toList();
        }
    }
}