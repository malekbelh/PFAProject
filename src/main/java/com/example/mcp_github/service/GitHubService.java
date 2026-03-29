package com.example.mcp_github.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.mcp_github.model.GitHubBranch;
import com.example.mcp_github.model.GitHubCollaborator;
import com.example.mcp_github.model.GitHubCommit;
import com.example.mcp_github.model.GitHubContent;
import com.example.mcp_github.model.GitHubFork;
import com.example.mcp_github.model.GitHubIssue;
import com.example.mcp_github.model.GitHubPullRequest;
import com.example.mcp_github.model.GitHubRelease;
import com.example.mcp_github.model.GitHubRepository;
import com.example.mcp_github.model.GitHubSearchResult;
import com.example.mcp_github.model.GitHubUser;
import com.example.mcp_github.model.GitHubWorkflowRun;
import com.example.mcp_github.model.GitHubWorkflowRunsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class GitHubService {

    private final WebClient webClient;
    private final boolean hasToken;

    public GitHubService(
            @Value("${github.api.base-url}") String baseUrl,
            @Value("${github.api.token:}") String token) {

        this.hasToken = token != null && !token.isEmpty();

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github.v3+json");

        if (token != null && !token.isEmpty()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }

        this.webClient = builder.build();
    }

    // ==================== REPOSITORIES ====================
    // For any user's PUBLIC repos
    public List<GitHubRepository> getUserRepositories(String username) {
        return webClient.get()
                .uri("/users/{username}/repos?sort=updated&per_page=100", username)
                .retrieve()
                .bodyToFlux(GitHubRepository.class)
                .collectList()
                .block();
    }

    // For authenticated user's ALL repos (public + private)
    public List<GitHubRepository> getAuthenticatedUserRepositories() {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required");
        }

        return webClient.get()
                .uri("/user/repos?per_page=100&type=all")
                .retrieve()
                .bodyToFlux(GitHubRepository.class)
                .collectList()
                .block();
    }

    // ==================== REPOSITORY MANAGEMENT ====================
    public GitHubRepository createRepository(String name, String description, boolean isPrivate) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to create repositories");
        }

        var requestBody = new CreateRepoRequest(name, description, isPrivate);

        return webClient.post()
                .uri("/user/repos")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GitHubRepository.class)
                .block();
    }

    public void deleteRepository(String username, String repo) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to delete repositories");
        }

        webClient.delete()
                .uri("/repos/{username}/{repo}", username, repo)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public GitHubRepository updateRepository(String username, String repo, String name, String description,
            boolean isPrivate) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to update repositories");
        }

        var requestBody = new UpdateRepoRequest(name, description, isPrivate);

        return webClient.patch()
                .uri("/repos/{username}/{repo}", username, repo)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GitHubRepository.class)
                .block();
    }

    // ==================== COMMITS ====================
    public List<GitHubCommit> getRepositoryCommits(String username, String repo, int limit) {
        int perPage = Math.min(limit, 100);

        return webClient.get()
                .uri("/repos/{username}/{repo}/commits?per_page={perPage}",
                        username, repo, perPage)
                .retrieve()
                .bodyToFlux(GitHubCommit.class)
                .collectList()
                .block();
    }

    public GitHubCommit getLastCommit(String username, String repo) {
        List<GitHubCommit> commits = webClient.get()
                .uri("/repos/{username}/{repo}/commits?per_page=1", username, repo)
                .retrieve()
                .bodyToFlux(GitHubCommit.class)
                .collectList()
                .block();

        return commits != null && !commits.isEmpty() ? commits.get(0) : null;
    }

    // ==================== COLLABORATORS ====================
    public List<GitHubCollaborator> getRepositoryCollaborators(String username, String repo) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/collaborators", username, repo)
                .retrieve()
                .bodyToFlux(GitHubCollaborator.class)
                .collectList()
                .block();
    }

    // ==================== ISSUES ====================
    public List<GitHubIssue> getRepositoryIssues(String username, String repo, String state, int limit) {
        int perPage = Math.min(limit, 100);

        return webClient.get()
                .uri("/repos/{username}/{repo}/issues?state={state}&per_page={perPage}",
                        username, repo, state, perPage)
                .retrieve()
                .bodyToFlux(GitHubIssue.class)
                .collectList()
                .block();
    }

    public GitHubIssue createIssue(String username, String repo, String title, String body) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to create issues");
        }

        var requestBody = new IssueRequest(title, body);

        return webClient.post()
                .uri("/repos/{username}/{repo}/issues", username, repo)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GitHubIssue.class)
                .block();
    }

    // ==================== PULL REQUESTS ====================
    public List<GitHubPullRequest> getRepositoryPullRequests(String username, String repo, String state, int limit) {
        int perPage = Math.min(limit, 100);

        return webClient.get()
                .uri("/repos/{username}/{repo}/pulls?state={state}&per_page={perPage}",
                        username, repo, state, perPage)
                .retrieve()
                .bodyToFlux(GitHubPullRequest.class)
                .collectList()
                .block();
    }

    public GitHubPullRequest createPullRequest(String username, String repo, String title, String head, String base,
            String body) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to create pull requests");
        }

        var requestBody = new CreatePRRequest(title, head, base, body);

        return webClient.post()
                .uri("/repos/{username}/{repo}/pulls", username, repo)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GitHubPullRequest.class)
                .block();
    }

    // ==================== BRANCHES ====================
    public List<GitHubBranch> getRepositoryBranches(String username, String repo) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/branches", username, repo)
                .retrieve()
                .bodyToFlux(GitHubBranch.class)
                .collectList()
                .block();
    }

    public GitHubBranch createBranch(String username, String repo, String branchName, String fromBranch) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to create branches");
        }

        // First, get the SHA of the commit from the source branch
        GitHubBranch sourceBranch = webClient.get()
                .uri("/repos/{username}/{repo}/branches/{branch}", username, repo, fromBranch)
                .retrieve()
                .bodyToMono(GitHubBranch.class)
                .block();

        if (sourceBranch == null) {
            throw new IllegalStateException("Source branch not found: " + fromBranch);
        }

        // Create the new branch reference
        var requestBody = new CreateRefRequest("refs/heads/" + branchName, sourceBranch.commit().sha());

        webClient.post()
                .uri("/repos/{username}/{repo}/git/refs", username, repo)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(RefResponse.class)
                .block();

        // Return the newly created branch
        return webClient.get()
                .uri("/repos/{username}/{repo}/branches/{branch}", username, repo, branchName)
                .retrieve()
                .bodyToMono(GitHubBranch.class)
                .block();
    }

    public void deleteBranch(String username, String repo, String branchName) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to delete branches");
        }

        webClient.delete()
                .uri("/repos/{username}/{repo}/git/refs/heads/{branch}", username, repo, branchName)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    // ==================== USER PROFILE ====================
    public GitHubUser getUserProfile(String username) {
        return webClient.get()
                .uri("/users/{username}", username)
                .retrieve()
                .bodyToMono(GitHubUser.class)
                .block();
    }

    public GitHubUser getAuthenticatedUserProfile() {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required");
        }

        return webClient.get()
                .uri("/user")
                .retrieve()
                .bodyToMono(GitHubUser.class)
                .block();
    }

    // ==================== RELEASES ====================
    public List<GitHubRelease> getRepositoryReleases(String username, String repo, int limit) {
        int perPage = Math.min(limit, 100);

        return webClient.get()
                .uri("/repos/{username}/{repo}/releases?per_page={perPage}",
                        username, repo, perPage)
                .retrieve()
                .bodyToFlux(GitHubRelease.class)
                .collectList()
                .block();
    }

    public GitHubRelease getLatestRelease(String username, String repo) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/releases/latest", username, repo)
                .retrieve()
                .bodyToMono(GitHubRelease.class)
                .block();
    }

    // ==================== GITHUB ACTIONS ====================
    public List<GitHubWorkflowRun> getWorkflowRuns(String username, String repo, int limit) {
        int perPage = Math.min(limit, 100);

        GitHubWorkflowRunsResponse response = webClient.get()
                .uri("/repos/{username}/{repo}/actions/runs?per_page={perPage}",
                        username, repo, perPage)
                .retrieve()
                .bodyToMono(GitHubWorkflowRunsResponse.class)
                .block();

        return response != null ? response.workflowRuns() : List.of();
    }

    // ==================== FILE CONTENT ====================
    public GitHubContent getFileContent(String username, String repo, String path) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/contents/{path}", username, repo, path)
                .retrieve()
                .bodyToMono(GitHubContent.class)
                .block();
    }

    // ==================== FILE OPERATIONS ====================
    public String pushFileContent(String username, String repo, String path, String content,
            String message, String branch) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to push files");
        }

        // Check if file exists to get its SHA (required for updates)
        String fileSha = null;
        try {
            GitHubContent existingFile = getFileContent(username, repo, path);
            if (existingFile != null) {
                fileSha = existingFile.sha();
            }
        } catch (Exception e) {
            // File doesn't exist, will be created
        }

        // Encode content to base64
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

        var requestBody = new PushFileRequest(message, encodedContent, fileSha, branch);

        PushFileResponse response = webClient.put()
                .uri("/repos/{username}/{repo}/contents/{path}", username, repo, path)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(PushFileResponse.class)
                .block();

        if (response != null && response.commit() != null) {
            return response.commit().sha();
        }
        return null;
    }

    public void deleteFile(String username, String repo, String path, String message, String branch) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to delete files");
        }

        // Get the file's SHA (required for deletion)
        GitHubContent file = getFileContent(username, repo, path);
        if (file == null) {
            throw new IllegalStateException("File not found: " + path);
        }

        var requestBody = new DeleteFileRequest(message, file.sha(), branch);

        webClient.method(HttpMethod.DELETE)
                .uri("/repos/{username}/{repo}/contents/{path}", username, repo, path)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    // ==================== SEARCH ====================
    public List<GitHubRepository> searchRepositories(String query, int limit) {
        int perPage = Math.min(limit, 100);

        GitHubSearchResult result = webClient.get()
                .uri("/search/repositories?q={query}&per_page={perPage}&sort=stars&order=desc",
                        query, perPage)
                .retrieve()
                .bodyToMono(GitHubSearchResult.class)
                .block();

        return result != null ? result.items() : List.of();
    }

    // ==================== FORKS ====================
    public List<GitHubFork> getRepositoryForks(String username, String repo, int limit) {
        int perPage = Math.min(limit, 100);

        return webClient.get()
                .uri("/repos/{username}/{repo}/forks?per_page={perPage}&sort=newest",
                        username, repo, perPage)
                .retrieve()
                .bodyToFlux(GitHubFork.class)
                .collectList()
                .block();
    }

    public GitHubRepository forkRepository(String username, String repo) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to fork repositories");
        }

        return webClient.post()
                .uri("/repos/{username}/{repo}/forks", username, repo)
                .retrieve()
                .bodyToMono(GitHubRepository.class)
                .block();
    }

    // ==================== STARRING ====================
    public void starRepository(String username, String repo) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to star repositories");
        }

        webClient.put()
                .uri("/user/starred/{username}/{repo}", username, repo)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public void unstarRepository(String username, String repo) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to unstar repositories");
        }

        webClient.delete()
                .uri("/user/starred/{username}/{repo}", username, repo)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public boolean isRepositoryStarred(String username, String repo) {
        if (!hasToken) {
            return false;
        }

        try {
            webClient.get()
                    .uri("/user/starred/{username}/{repo}", username, repo)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== HELPER ====================
    public boolean hasAuthentication() {
        return hasToken;
    }

    public void mergePullRequest(String username, String repo, int prNumber, String commitMessage) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token required to merge pull requests");
        }

        webClient.put()
                .uri("/repos/{username}/{repo}/pulls/{prNumber}/merge", username, repo, prNumber)
                .bodyValue(new MergeRequest(commitMessage, "merge"))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    // ==================== DTO RECORDS ====================

    private record CreateRepoRequest(String name, String description, @JsonProperty("private") boolean isPrivate) {
    }

    private record UpdateRepoRequest(String name, String description, @JsonProperty("private") boolean isPrivate) {
    }

    private record IssueRequest(String title, String body) {
    }

    private record CreatePRRequest(String title, String head, String base, String body) {
    }

    private record CreateRefRequest(String ref, String sha) {
    }

    private record RefResponse(String ref, String url, GitHubBranch.GitHubCommitRef object) {
    }

    private record PushFileRequest(String message, String content, String sha, String branch) {
    }

    private record PushFileResponse(GitHubContent content, PushFileCommit commit) {
    }

    private record PushFileCommit(String sha) {
    }

    private record DeleteFileRequest(String message, String sha, String branch) {
    }

    private record MergeRequest(@JsonProperty("commit_message") String commitMessage,
            @JsonProperty("merge_method") String mergeMethod) {
    }

}