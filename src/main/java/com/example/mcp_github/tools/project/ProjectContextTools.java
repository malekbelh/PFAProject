package com.example.mcp_github.tools.project;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_github.service.MemoryService;
import com.example.mcp_github.service.ProjectContextService;
import com.example.mcp_github.service.ProjectContextService.GitProjectContext;

@Component
public class ProjectContextTools {

    private final ProjectContextService projectContextService;
    private final MemoryService memoryService;

    public ProjectContextTools(ProjectContextService projectContextService,
            MemoryService memoryService) {
        this.projectContextService = projectContextService;
        this.memoryService = memoryService;
    }

    @Tool(name = "initializeProject", description = """
            MANDATORY: Call this tool automatically at the very beginning of EVERY conversation.
            You should pass the Absolute Path to the current workspace you are in, as known from your <user_information> context.
            Always re-reads .git/HEAD and .git/config from the provided workspace — never returns
            stale data. If the user has switched projects or branches, the change is picked up
            immediately.
            """)
    public String initializeProject(
            @ToolParam(description = "The absolute path of the user's current project workspace.") String workspacePath) {

        Optional<GitProjectContext> ctx;
        if (workspacePath != null && !workspacePath.isBlank()) {
            ctx = projectContextService.detectFromDirectory(workspacePath);
        } else {
            ctx = projectContextService.detectFromCurrentDirectory();
        }

        if (ctx.isPresent()) {
            GitProjectContext project = ctx.get();

            if (!Files.isDirectory(Paths.get(project.projectPath()))) {
                return """
                        ⚠️ Detected project path is no longer accessible: %s
                        Solutions:
                        • Make sure the project folder is still open
                        • Or call: setCurrentProject(owner, repo, branch)
                        """.formatted(project.projectPath());
            }

            String savedOwner = memoryService.recall("current_owner");
            String savedRepo = memoryService.recall("current_repo");
            String savedBranch = memoryService.recall("current_branch");

            // ✅ FIX NPE — Objects.equals() gère null des deux côtés
            // ✅ FIX PATH — inclure current_path dans la détection de changement
            String savedPath = memoryService.recall("current_path");
            boolean changed = !Objects.equals(project.owner(), savedOwner)
                    || !Objects.equals(project.repo(), savedRepo)
                    || !Objects.equals(project.branch(), savedBranch)
                    || !Objects.equals(project.projectPath(), savedPath);

            memoryService.remember("current_owner", project.owner());
            memoryService.remember("current_repo", project.repo());
            memoryService.remember("current_branch", project.branch());
            memoryService.remember("current_path", project.projectPath());

            if (changed) {
                String prev = savedOwner != null
                        ? "%s/%s [%s]".formatted(savedOwner, savedRepo, savedBranch)
                        : "none";
                return """
                        🔄 Project changed — updated automatically!

                        Previous : %s
                        Owner    : %s
                        Repo     : %s
                        Branch   : %s
                        Path     : %s

                        🔗 https://github.com/%s/%s

                        ✅ Memory and file-save path updated to the new project.
                        """.formatted(prev,
                        project.owner(), project.repo(),
                        project.branch(), project.projectPath(),
                        project.owner(), project.repo());
            } else {
                return """
                        ✅ Active project (freshly verified from disk):

                        Owner  : %s
                        Repo   : %s
                        Branch : %s
                        Path   : %s

                        🔗 https://github.com/%s/%s
                        """.formatted(
                        project.owner(), project.repo(),
                        project.branch(), project.projectPath(),
                        project.owner(), project.repo());
            }
        }

        // Aucun .git trouvé — fallback mémoire
        String savedOwner = memoryService.recall("current_owner");
        String savedRepo = memoryService.recall("current_repo");
        String savedBranch = memoryService.recall("current_branch");
        String savedPath = memoryService.recall("current_path");

        if (savedOwner != null && savedRepo != null) {
            boolean pathOk = savedPath != null
                    && !savedPath.equals("manual")
                    && Files.isDirectory(Paths.get(savedPath));

            return """
                    ⚠️ No .git directory found in workspace — loaded from memory:

                    Owner  : %s
                    Repo   : %s
                    Branch : %s
                    Path   : %s  %s

                    🔗 https://github.com/%s/%s

                    💡 Open a folder with .git or set PROJECT_PATH env var to enable auto-detection.
                    %s
                    """.formatted(
                    savedOwner, savedRepo,
                    savedBranch != null ? savedBranch : "main",
                    savedPath != null ? savedPath : "N/A",
                    pathOk ? "✅" : "⚠️ (path not accessible — files cannot be saved there)",
                    savedOwner, savedRepo,
                    pathOk ? "" : "⚠️ Run `detectProjectFromPath` to fix the save path.");
        }

        return """
                ⚠️ No active project detected.
                Solutions:
                • Set the PROJECT_PATH environment variable to your project folder
                • Open a folder containing .git in your workspace
                • Or call: setCurrentProject(owner, repo, branch)
                """;
    }

    @Tool(name = "detectCurrentProject", description = """
            Force re-detection of the active GitHub project from the open workspace.
            Use this after switching projects or branches if auto-detection hasn't fired.
            """)
    public String detectCurrentProject() {
        Optional<GitProjectContext> ctx = projectContextService.detectFromCurrentDirectory();
        if (ctx.isEmpty()) {
            return "❌ No Git project found in the current workspace.";
        }
        return saveAndConfirm(ctx.get(), "detected from workspace");
    }

    @Tool(name = "detectProjectFromPath", description = """
            Detect the GitHub project from a specific folder path supplied by the user.
            Use this when the project is not being auto-detected (e.g. PROJECT_PATH not set).
            """)
    public String detectProjectFromPath(
            @ToolParam(description = "Absolute path to the project folder.") String path) {

        if (path == null || path.isBlank()) {
            return "❌ Invalid path.";
        }
        if (!Files.isDirectory(Paths.get(path))) {
            return "❌ Path does not exist or is not a directory: " + path;
        }

        Optional<GitProjectContext> ctx = projectContextService.detectFromDirectory(path);
        if (ctx.isEmpty()) {
            return "❌ No Git project found in: " + path
                    + "\n   Make sure this folder contains a .git subdirectory.";
        }

        return saveAndConfirm(ctx.get(), "detected from path: " + path);
    }

    @Tool(name = "getCurrentProject", description = "Show the currently active GitHub project from persistent memory.")
    public String getCurrentProject() {
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");
        String branch = memoryService.recall("current_branch");
        String path = memoryService.recall("current_path");

        if (owner == null || repo == null) {
            return "⚠️ No active project in memory.";
        }

        boolean pathOk = path != null
                && !path.equals("manual")
                && Files.isDirectory(Paths.get(path));

        return """
                📦 Active project:

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s  %s

                🔗 URL : https://github.com/%s/%s
                """.formatted(owner, repo, branch, path,
                pathOk ? "✅" : "⚠️ (path not accessible)",
                owner, repo);
    }

    @Tool(name = "setCurrentProject", description = "Manually set the active GitHub project (owner, repo, branch).")
    public String setCurrentProject(
            @ToolParam(description = "GitHub owner username") String owner,
            @ToolParam(description = "GitHub repository name") String repo,
            @ToolParam(description = "Active branch name") String branch) {

        if (owner == null || owner.isBlank()) {
            return "❌ 'owner' is required.";
        }
        if (repo == null || repo.isBlank()) {
            return "❌ 'repo' is required.";
        }
        if (branch == null || branch.isBlank()) {
            branch = "main";
        }

        memoryService.remember("current_owner", owner.trim());
        memoryService.remember("current_repo", repo.trim());
        memoryService.remember("current_branch", branch.trim());
        memoryService.remember("current_path", "manual");

        return """
                ✅ Project set manually!

                Owner  : %s
                Repo   : %s
                Branch : %s

                ⚠️ Path set to 'manual' — run `detectProjectFromPath` if you want
                   rollo.md to be saved at the correct project root.

                🔗 https://github.com/%s/%s
                """.formatted(owner, repo, branch, owner, repo);
    }

    @Tool(name = "refreshCurrentBranch", description = "Re-read .git/HEAD to update the current branch in memory.")
    public String refreshCurrentBranch() {
        String path = memoryService.recall("current_path");
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");

        if (owner == null || repo == null) {
            return "⚠️ No active project in memory.";
        }
        if (path == null || path.equals("manual")) {
            return "⚠️ Project was set manually — branch cannot be auto-refreshed.";
        }

        Optional<GitProjectContext> ctx = projectContextService.detectFromDirectory(path);
        if (ctx.isEmpty()) {
            return "❌ Cannot re-read project from: " + path;
        }

        String newBranch = ctx.get().branch();
        String oldBranch = memoryService.recall("current_branch");
        memoryService.remember("current_branch", newBranch);

        return Objects.equals(newBranch, oldBranch)
                ? "✅ Branch unchanged: " + newBranch
                : "🔄 Branch updated: %s → %s".formatted(oldBranch, newBranch);
    }

    @Tool(name = "clearCurrentProject", description = "Remove the active project from persistent memory.")
    public String clearCurrentProject() {
        String owner = memoryService.recall("current_owner");
        String repo = memoryService.recall("current_repo");

        memoryService.forget("current_owner");
        memoryService.forget("current_repo");
        memoryService.forget("current_branch");
        memoryService.forget("current_path");

        return owner == null
                ? "ℹ️ No active project in memory."
                : "🗑️ Project %s/%s removed from memory.".formatted(owner, repo);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────
    private String saveAndConfirm(GitProjectContext project, String source) {
        String savedOwner = memoryService.recall("current_owner");
        String savedRepo = memoryService.recall("current_repo");
        String savedBranch = memoryService.recall("current_branch");
        String savedPath = memoryService.recall("current_path");

        boolean changed = !Objects.equals(project.owner(), savedOwner)
                || !Objects.equals(project.repo(), savedRepo)
                || !Objects.equals(project.branch(), savedBranch)
                || !Objects.equals(project.projectPath(), savedPath);

        memoryService.remember("current_owner", project.owner());
        memoryService.remember("current_repo", project.repo());
        memoryService.remember("current_branch", project.branch());
        memoryService.remember("current_path", project.projectPath());

        String status = changed ? "🔄 Project updated / changed" : "✅ Project unchanged";

        return """
                %s (%s)!

                Owner  : %s
                Repo   : %s
                Branch : %s
                Path   : %s

                🔗 https://github.com/%s/%s
                """.formatted(status, source,
                project.owner(), project.repo(),
                project.branch(), project.projectPath(),
                project.owner(), project.repo());
    }
}
