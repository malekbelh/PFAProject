package com.example.mcp_github;

import java.util.Objects;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.mcp_github.service.MemoryService;
import com.example.mcp_github.service.ProjectContextService;
import com.example.mcp_github.service.WorkspaceResolver;

@Component
public class ProjectAutoDetector implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectAutoDetector.class);

    private final ProjectContextService projectContextService;
    private final MemoryService memoryService;
    private final WorkspaceResolver workspaceResolver;

    public ProjectAutoDetector(ProjectContextService projectContextService,
            MemoryService memoryService,
            WorkspaceResolver workspaceResolver) {
        this.projectContextService = projectContextService;
        this.memoryService = memoryService;
        this.workspaceResolver = workspaceResolver;
    }

    @Override
    public void run(ApplicationArguments args) {

        // ── 1. Résolution du workspace via le composant partagé ───────────────
        String workspace = workspaceResolver.resolve();
        if (workspace == null) {
            log.warn("Aucun chemin de projet configuré — détection automatique ignorée");
            return;
        }

        // ── 2. Toujours tenter une détection fraîche depuis le workspace ──────
        log.info("Détection du projet depuis le workspace : {}", workspace);

        var ctx = projectContextService.detectFromDirectory(workspace);
        if (ctx.isPresent()) {
            String newOwner = ctx.get().owner();
            String newRepo = ctx.get().repo();
            String newBranch = ctx.get().branch();
            String newPath = ctx.get().projectPath();

            String savedOwner = memoryService.recall("current_owner");
            String savedRepo = memoryService.recall("current_repo");
            String savedBranch = memoryService.recall("current_branch");

            boolean changed = !Objects.equals(newOwner, savedOwner)
                    || !Objects.equals(newRepo, savedRepo)
                    || !Objects.equals(newBranch, savedBranch);

            if (changed) {
                log.warn("Projet modifié : {}/{} [{}] → {}/{} [{}]",
                        savedOwner, savedRepo, savedBranch,
                        newOwner, newRepo, newBranch);
            } else {
                log.info("Projet inchangé : {}/{} [{}]", newOwner, newRepo, newBranch);
            }

            save(newOwner, newRepo, newBranch, newPath);
            return;
        }

        // ── 3. Fallback — CLI git ─────────────────────────────────────────────
        log.warn("Échec de lecture de .git/config — tentative via la CLI git...");
        try {
            String remoteUrl = runGit(workspace, "git", "remote", "get-url", "origin");
            String branch = runGit(workspace, "git", "rev-parse", "--abbrev-ref", "HEAD");

            if (remoteUrl == null || remoteUrl.isBlank()) {
                log.warn("Aucun remote 'origin' trouvé dans {}", workspace);
                return;
            }

            String[] parts = parseGitUrl(remoteUrl);
            if (parts == null) {
                log.warn("URL remote non reconnue : {}", remoteUrl);
                return;
            }

            save(parts[0], parts[1], branch != null ? branch : "main", workspace);

        } catch (Exception e) {
            log.warn("Erreur de détection git : {}", e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private void save(String owner, String repo, String branch, String path) {
        memoryService.remember("current_owner", owner);
        memoryService.remember("current_repo", repo);
        memoryService.remember("current_branch", branch);
        memoryService.remember("current_path", path);
        log.warn("Projet sauvegardé : {}/{} [{}] @ {}", owner, repo, branch, path);
    }

    private String runGit(String workdir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(workdir));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.debug("Commande git terminée avec {} : {}", exitCode, String.join(" ", command));
                    return null;
                }
                return line != null ? line.trim() : null;
            }
        } catch (Exception e) {
            log.warn("Commande git échouée : {}", e.getMessage());
            return null;
        }
    }

    private String[] parseGitUrl(String url) {
        url = url.trim().replace(".git", "");
        if (url.contains("github.com/")) {
            String path = url.substring(url.indexOf("github.com/") + "github.com/".length());
            String[] parts = path.split("/");
            if (parts.length >= 2) {
                return new String[]{parts[0], parts[1]};
            }
        }
        if (url.contains("github.com:")) {
            String path = url.substring(url.indexOf("github.com:") + "github.com:".length());
            String[] parts = path.split("/");
            if (parts.length >= 2) {
                return new String[]{parts[0], parts[1]};
            }
        }
        return null;
    }
}
