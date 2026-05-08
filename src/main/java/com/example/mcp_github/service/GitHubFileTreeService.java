package com.example.mcp_github.service;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Service
public class GitHubFileTreeService {

    private static final Logger log = LoggerFactory.getLogger(GitHubFileTreeService.class);

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt",
            ".ts", ".tsx", ".js", ".jsx",
            ".py",
            ".go",
            ".rs",
            ".cs",
            ".cpp", ".cc", ".h", ".hpp",
            ".rb"
    );
    private static final Set<String> ENTRY_POINT_NAMES = Set.of(
            "main", "app", "application", "index",
            "server", "bootstrap", "program", "startup");

    private static final Set<String> CONFIG_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "package.json", "requirements.txt", "setup.py", "pyproject.toml",
            "go.mod", "Cargo.toml", "*.csproj", "Gemfile");

    private static final Set<String> IGNORED_PREFIXES = Set.of(
            "node_modules/", ".git/", "dist/", "build/", "target/",
            ".mvn/", "__pycache__/", ".venv/", "venv/", "vendor/",
            "coverage/", ".idea/", ".vscode/");

    private static final int MAX_SOURCE_FILES = 25;
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB

    private final WebClient webClient;
    private final com.example.mcp_github.service.rollodocs.GitHubResponseCache cache;

    public GitHubFileTreeService(
            @Value("${github.api.base-url}") String baseUrl,
            @Value("${github.api.token:}") String token,
            com.example.mcp_github.service.rollodocs.GitHubResponseCache cache) {

        this.cache = cache;

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
                log.warn("Arborescence vide reçue pour {}/{} [{}]", owner, repo, resolvedBranch);
                return List.of();
            }

            if (response.truncated()) {
                log.warn("Arborescence tronquée pour {}/{} [{}] — le dépôt dépasse la limite GitHub",
                        owner, repo, resolvedBranch);
            }

            return response.tree().stream()
                    .filter(e -> "blob".equals(e.type()))
                    .filter(e -> !isIgnored(e.path()))
                    .toList();

        } catch (WebClientResponseException e) {
            log.error("Erreur HTTP {} lors de la récupération de l'arborescence de {}/{} [{}] : {}",
                    e.getStatusCode(), owner, repo, resolvedBranch, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Erreur inattendue lors de la récupération de l'arborescence de {}/{} [{}] : {}",
                    owner, repo, resolvedBranch, e.getMessage());
            return List.of();
        }
    }

    public Map<String, String> fetchKeyFiles(String owner, String repo,
            String branch, List<TreeEntry> tree) {

        List<String> paths = tree.stream().map(TreeEntry::path).toList();

        List<String> toFetch = paths.stream()
                .filter(this::isConfigFile)
                .toList();

        List<String> entryPoints = paths.stream()
                .filter(p -> !isConfigFile(p))
                .filter(this::isSourceFile)
                .filter(this::isEntryPoint)
                .toList();

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
                log.debug("Fichier ignoré (binaire ou trop grand) : {}", path);
            }
        }

        return result;
    }

    public RepositorySnapshot snapshot(String owner, String repo, String branch) {
        List<TreeEntry> tree = getFileTree(owner, repo, branch);
        Map<String, String> keyFiles = fetchKeyFiles(owner, repo, branch, tree);
        return new RepositorySnapshot(owner, repo, branch, tree, keyFiles);
    }

    public String fetchFileContent(String owner, String repo, String branch, String path) {
        String cached = (String) cache.get(owner, repo, path, "content");
        if (cached != null) {
            return cached;
        }

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

            String cleaned = response.content().replace("\n", "").trim();
            String decoded = new String(Base64.getDecoder().decode(cleaned));
            cache.put(owner, repo, path, "content", decoded);
            return decoded;

        } catch (WebClientResponseException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.debug("Impossible de récupérer le contenu de {} : {}", path, e.getMessage());
            return null;
        }
    }

    private boolean isIgnored(String path) {
        for (String prefix : IGNORED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSourceFile(String path) {
        for (String ext : SOURCE_EXTENSIONS) {
            if (path.endsWith(ext)) {
                return true;
            }
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
            String type,
            String sha,
            @JsonProperty("size") Long size) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileContentResponse(
            String name,
            String path,
            String content,
            String encoding,
            String sha) {

    }

    public record RepositorySnapshot(
            String owner,
            String repo,
            String branch,
            List<TreeEntry> tree,
            Map<String, String> keyFiles) {

        public List<String> allPaths() {
            return tree.stream().map(TreeEntry::path).toList();
        }
    }
}
