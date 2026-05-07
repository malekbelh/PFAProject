package com.example.mcp_github.tools.repository;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_github.model.GitHubRepository;
import com.example.mcp_github.service.GitHubService;

/**
 * MCP Tools — Repository domain. Covers: listing, creating, updating, deleting
 * and searching repositories.
 */
@Component
public class GitHubRepositoryTools {

    private final GitHubService gitHubService;

    public GitHubRepositoryTools(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @Tool(name = "getUserRepositories",
            description = "Get all public repositories for a given GitHub user, sorted by last updated date.")
    public String getUserRepositories(
            @ToolParam(description = "The GitHub username") String username) {
        try {
            List<GitHubRepository> repos = gitHubService.getUserRepositories(username);
            if (repos == null || repos.isEmpty()) {
                return "No repositories found for user: " + username;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Found %d repositories for user '%s':\n\n".formatted(repos.size(), username));
            repos.forEach(r -> appendRepoSummary(sb, r));
            return sb.toString();
        } catch (Exception e) {
            return "Error fetching repositories for user '%s': %s".formatted(username, e.getMessage());
        }
    }

    @Tool(name = "getMyRepositories",
            description = "Get all repositories (public + private) for the authenticated user. Requires a GitHub token.")
    public String getMyRepositories() {
        try {
            requireAuth();
            List<GitHubRepository> repos = gitHubService.getAuthenticatedUserRepositories();
            if (repos == null || repos.isEmpty()) {
                return "No repositories found for the authenticated user.";
            }
            long privateCount = repos.stream().filter(GitHubRepository::isPrivate).count();
            long publicCount = repos.size() - privateCount;
            StringBuilder sb = new StringBuilder();
            sb.append("Found %d repositories (%d public, %d private):\n\n"
                    .formatted(repos.size(), publicCount, privateCount));
            for (GitHubRepository repo : repos) {
                sb.append(repo.isPrivate() ? "🔒 PRIVATE " : "🌐 PUBLIC  ");
                appendRepoSummary(sb, repo);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error fetching repositories: " + e.getMessage();
        }
    }

    @Tool(name = "createRepository",
            description = "Create a new GitHub repository. Requires authentication.")
    public String createRepository(
            @ToolParam(description = "Repository name") String name,
            @ToolParam(description = "Short description of the repository") String description,
            @ToolParam(description = "true to make the repository private") Boolean isPrivate) {
        try {
            requireAuth();
            GitHubRepository repo = gitHubService.createRepository(
                    name,
                    description != null ? description : "",
                    Boolean.TRUE.equals(isPrivate));
            return formatRepoResult("✅ Repository created successfully!", repo);
        } catch (Exception e) {
            return "Error creating repository '%s': %s".formatted(name, e.getMessage());
        }
    }

    @Tool(name = "updateRepository",
            description = "Update a repository name, description or visibility. Requires authentication.")
    public String updateRepository(
            @ToolParam(description = "Owner username") String username,
            @ToolParam(description = "Current repository name") String repository,
            @ToolParam(description = "New repository name (keep same to leave unchanged)") String newName,
            @ToolParam(description = "New description") String description,
            @ToolParam(description = "true to make private, false to make public") Boolean isPrivate) {
        try {
            requireAuth();
            String repoName = (newName != null && !newName.isBlank()) ? newName : repository;
            GitHubRepository repo = gitHubService.updateRepository(
                    username, repository,
                    repoName,
                    description != null ? description : "",
                    Boolean.TRUE.equals(isPrivate));
            return formatRepoResult("✅ Repository updated successfully!", repo);
        } catch (Exception e) {
            return "Error updating repository '%s/%s': %s".formatted(username, repository, e.getMessage());
        }
    }

    @Tool(name = "deleteRepository",
            description = "Permanently delete a GitHub repository. Requires authentication. ⚠️ Irreversible.")
    public String deleteRepository(
            @ToolParam(description = "Owner username") String username,
            @ToolParam(description = "Repository name") String repository) {
        try {
            requireAuth();
            gitHubService.deleteRepository(username, repository);
            return "🗑️ Repository %s/%s deleted successfully.".formatted(username, repository);
        } catch (Exception e) {
            return "Error deleting repository '%s/%s': %s".formatted(username, repository, e.getMessage());
        }
    }

    @Tool(name = "searchRepositories",
            description = "Search GitHub repositories. Example queries: 'spring boot', 'language:java stars:>1000'.")
    public String searchRepositories(
            @ToolParam(description = "Search query string") String query,
            @ToolParam(description = "Max results (default 10, max 100)") Integer limit) {
        try {
            List<GitHubRepository> repos = gitHubService.searchRepositories(query, resolveLimit(limit));
            if (repos == null || repos.isEmpty()) {
                return "No repositories found for query: " + query;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Search results for '%s':\n\n".formatted(query));
            for (int i = 0; i < repos.size(); i++) {
                GitHubRepository r = repos.get(i);
                sb.append("%d. 📦 %s\n".formatted(i + 1, r.fullName()));
                sb.append("   Description: %s\n".formatted(nullOr(r.description(), "No description")));
                sb.append("   Language   : %s\n".formatted(nullOr(r.language(), "Unknown")));
                sb.append("   ⭐ Stars: %d | 🍴 Forks: %d\n".formatted(r.stars(), r.forks()));
                sb.append("   URL        : %s\n\n".formatted(r.htmlUrl()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error searching repositories: " + e.getMessage();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────
    private void requireAuth() {
        if (!gitHubService.hasAuthentication()) {
            throw new IllegalStateException("GitHub token is not configured.");
        }
    }

    private void appendRepoSummary(StringBuilder sb, GitHubRepository r) {
        sb.append("📦 %s\n".formatted(r.name()));
        sb.append("   Description : %s\n".formatted(nullOr(r.description(), "No description")));
        sb.append("   Language    : %s\n".formatted(nullOr(r.language(), "Unknown")));
        sb.append("   ⭐ Stars: %d | 🍴 Forks: %d\n".formatted(r.stars(), r.forks()));
        sb.append("   URL         : %s\n".formatted(r.htmlUrl()));
        sb.append("   Last updated: %s\n\n".formatted(r.updatedAt()));
    }

    private String formatRepoResult(String header, GitHubRepository r) {
        return header + "\n\n"
                + "Name       : " + r.name() + "\n"
                + "Description: " + nullOr(r.description(), "No description") + "\n"
                + "Visibility : " + (r.isPrivate() ? "🔒 Private" : "🌐 Public") + "\n"
                + "URL        : " + r.htmlUrl() + "\n";
    }

    private int resolveLimit(Integer limit) {
        return (limit != null && limit > 0) ? Math.min(limit, 100) : 10;
    }

    private String nullOr(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
