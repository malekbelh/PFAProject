package com.example.mcp_github.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    // ✅ FIX #5b — logger ajouté pour tracer les erreurs réseau dans isRepositoryStarred
    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

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
    public List<GitHubRepository> getUserRepositories(String username) {
        return webClient.get()
                .uri("/users/{username}/repos?sort=updated&per_page=100", username)
                .retrieve()
                .bodyToFlux(GitHubRepository.class)
                .collectList()
                .block();
    }

    public List<GitHubRepository> getAuthenticatedUserRepositories() {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis");
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
            throw new IllegalStateException("GitHub token requis pour créer un dépôt");
        }
        return webClient.post()
                .uri("/user/repos")
                .bodyValue(new CreateRepoRequest(name, description, isPrivate))
                .retrieve()
                .bodyToMono(GitHubRepository.class)
                .block();
    }

    public void deleteRepository(String username, String repo) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour supprimer un dépôt");
        }
        webClient.delete()
                .uri("/repos/{username}/{repo}", username, repo)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public GitHubRepository updateRepository(String username, String repo, String name,
            String description, boolean isPrivate) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour modifier un dépôt");
        }
        return webClient.patch()
                .uri("/repos/{username}/{repo}", username, repo)
                .bodyValue(new UpdateRepoRequest(name, description, isPrivate))
                .retrieve()
                .bodyToMono(GitHubRepository.class)
                .block();
    }

    // ==================== COMMITS ====================
    public List<GitHubCommit> getRepositoryCommits(String username, String repo, int limit) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/commits?per_page={perPage}", username, repo, Math.min(limit, 100))
                .retrieve()
                .bodyToFlux(GitHubCommit.class)
                .collectList()
                .block();
    }

    public List<GitHubCommit> getRepositoryCommitsByPath(String username, String repo, String path, int limit) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/commits?path={path}&per_page={perPage}", username, repo, path, Math.min(limit, 100))
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
        return webClient.get()
                .uri("/repos/{username}/{repo}/issues?state={state}&per_page={perPage}",
                        username, repo, state, Math.min(limit, 100))
                .retrieve()
                .bodyToFlux(GitHubIssue.class)
                .collectList()
                .block();
    }

    public GitHubIssue createIssue(String username, String repo, String title, String body) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour créer une issue");
        }
        return webClient.post()
                .uri("/repos/{username}/{repo}/issues", username, repo)
                .bodyValue(new IssueRequest(title, body))
                .retrieve()
                .bodyToMono(GitHubIssue.class)
                .block();
    }

    // ==================== PULL REQUESTS ====================
    public List<GitHubPullRequest> getRepositoryPullRequests(String username, String repo,
            String state, int limit) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/pulls?state={state}&per_page={perPage}",
                        username, repo, state, Math.min(limit, 100))
                .retrieve()
                .bodyToFlux(GitHubPullRequest.class)
                .collectList()
                .block();
    }

    public GitHubPullRequest createPullRequest(String username, String repo, String title,
            String head, String base, String body) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour créer une PR");
        }
        return webClient.post()
                .uri("/repos/{username}/{repo}/pulls", username, repo)
                .bodyValue(new CreatePRRequest(title, head, base, body))
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
            throw new IllegalStateException("GitHub token requis pour créer une branche");
        }

        GitHubBranch sourceBranch = webClient.get()
                .uri("/repos/{username}/{repo}/branches/{branch}", username, repo, fromBranch)
                .retrieve()
                .bodyToMono(GitHubBranch.class)
                .block();

        if (sourceBranch == null) {
            throw new IllegalStateException("Branche source introuvable : " + fromBranch);
        }

        webClient.post()
                .uri("/repos/{username}/{repo}/git/refs", username, repo)
                .bodyValue(new CreateRefRequest("refs/heads/" + branchName, sourceBranch.commit().sha()))
                .retrieve()
                .bodyToMono(RefResponse.class)
                .block();

        return webClient.get()
                .uri("/repos/{username}/{repo}/branches/{branch}", username, repo, branchName)
                .retrieve()
                .bodyToMono(GitHubBranch.class)
                .block();
    }

    public void deleteBranch(String username, String repo, String branchName) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour supprimer une branche");
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
            throw new IllegalStateException("GitHub token requis");
        }
        return webClient.get()
                .uri("/user")
                .retrieve()
                .bodyToMono(GitHubUser.class)
                .block();
    }

    // ==================== RELEASES ====================
    public List<GitHubRelease> getRepositoryReleases(String username, String repo, int limit) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/releases?per_page={perPage}", username, repo, Math.min(limit, 100))
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
        GitHubWorkflowRunsResponse response = webClient.get()
                .uri("/repos/{username}/{repo}/actions/runs?per_page={perPage}", username, repo, Math.min(limit, 100))
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
    public String pushFileContent(String username, String repo, String path,
            String content, String message, String branch) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour pousser un fichier");
        }

        String fileSha = null;
        try {
            GitHubContent existing = getFileContent(username, repo, path);
            if (existing != null) {
                fileSha = existing.sha();
            }
        } catch (Exception ignored) {
        }

        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        PushFileResponse response = webClient.put()
                .uri("/repos/{username}/{repo}/contents/{path}", username, repo, path)
                .bodyValue(new PushFileRequest(message, encodedContent, fileSha, branch))
                .retrieve()
                .bodyToMono(PushFileResponse.class)
                .block();

        return (response != null && response.commit() != null) ? response.commit().sha() : null;
    }

    public void deleteFile(String username, String repo, String path, String message, String branch) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour supprimer un fichier");
        }
        GitHubContent file = getFileContent(username, repo, path);
        if (file == null) {
            throw new IllegalStateException("Fichier introuvable : " + path);
        }
        webClient.method(HttpMethod.DELETE)
                .uri("/repos/{username}/{repo}/contents/{path}", username, repo, path)
                .bodyValue(new DeleteFileRequest(message, file.sha(), branch))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    // ==================== SEARCH ====================
    public List<GitHubRepository> searchRepositories(String query, int limit) {
        GitHubSearchResult result = webClient.get()
                .uri("/search/repositories?q={query}&per_page={perPage}&sort=stars&order=desc",
                        query, Math.min(limit, 100))
                .retrieve()
                .bodyToMono(GitHubSearchResult.class)
                .block();
        return result != null ? result.items() : List.of();
    }

    // ==================== FORKS ====================
    public List<GitHubFork> getRepositoryForks(String username, String repo, int limit) {
        return webClient.get()
                .uri("/repos/{username}/{repo}/forks?per_page={perPage}&sort=newest",
                        username, repo, Math.min(limit, 100))
                .retrieve()
                .bodyToFlux(GitHubFork.class)
                .collectList()
                .block();
    }

    public GitHubRepository forkRepository(String username, String repo) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour forker un dépôt");
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
            throw new IllegalStateException("GitHub token requis pour starrer un dépôt");
        }
        webClient.put()
                .uri("/user/starred/{username}/{repo}", username, repo)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    public void unstarRepository(String username, String repo) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour retirer une étoile");
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
        } catch (WebClientResponseException.NotFound e) {
            // 404 = dépôt non starred : comportement normal, pas d'erreur à logger
            return false;
        } catch (Exception e) {
            // Erreur réseau ou auth — distincte du cas "pas starred"
            log.debug("Impossible de vérifier le star de {}/{} : {}", username, repo, e.getMessage());
            return false;
        }
    }

    // ==================== HELPER ====================
    public boolean hasAuthentication() {
        return hasToken;
    }

    public void mergePullRequest(String username, String repo, int prNumber, String commitMessage) {
        if (!hasToken) {
            throw new IllegalStateException("GitHub token requis pour merger une PR");
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

    private record MergeRequest(
            @JsonProperty("commit_message") String commitMessage,
            @JsonProperty("merge_method") String mergeMethod) {

    }
}
