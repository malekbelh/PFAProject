# Diagramme de classes

```mermaid
classDiagram
class GitHubService {
  +getUserRepositories(String username) List<GitHubRepository>
  +getAuthenticatedUserRepositories() List<GitHubRepository>
  +createRepository(String name, String description, boolean isPrivate) GitHubRepository
  +deleteRepository(String username, String repo) void
  +updateRepository(String username, String repo, String name, String description, boolean isPrivate) GitHubRepository
  +getRepositoryCommits(String username, String repo, int limit) List<GitHubCommit>
  +getRepositoryCommitsByPath(String username, String repo, String path, int limit) List<GitHubCommit>
  +getLastCommit(String username, String repo) GitHubCommit
  +getRepositoryCollaborators(String username, String repo) List<GitHubCollaborator>
  +getRepositoryIssues(String username, String repo, String state, int limit) List<GitHubIssue>
  +createIssue(String username, String repo, String title, String body) GitHubIssue
  +getRepositoryPullRequests(String username, String repo, String state, int limit) List<GitHubPullRequest>
  +createPullRequest(String username, String repo, String title, String head, String base, String body) GitHubPullRequest
  +getRepositoryBranches(String username, String repo) List<GitHubBranch>
  +createBranch(String username, String repo, String branchName, String fromBranch) GitHubBranch
  +deleteBranch(String username, String repo, String branchName) void
  +getUserProfile(String username) GitHubUser
  +getAuthenticatedUserProfile() GitHubUser
  +getRepositoryReleases(String username, String repo, int limit) List<GitHubRelease>
  +getLatestRelease(String username, String repo) GitHubRelease
  +getWorkflowRuns(String username, String repo, int limit) List<GitHubWorkflowRun>
  +getFileContent(String username, String repo, String path) GitHubContent
  +pushFileContent(String username, String repo, String path, String content, String message, String branch) String
  +deleteFile(String username, String repo, String path, String message, String branch) void
  +searchRepositories(String query, int limit) List<GitHubRepository>
  +getRepositoryForks(String username, String repo, int limit) List<GitHubFork>
  +forkRepository(String username, String repo) GitHubRepository
  +starRepository(String username, String repo) void
  +unstarRepository(String username, String repo) void
  +isRepositoryStarred(String username, String repo) boolean
  +hasAuthentication() boolean
  +mergePullRequest(String username, String repo, int prNumber, String commitMessage) void
}
GitHubService --> GitHubBranch
GitHubService --> GitHubCollaborator
GitHubService --> GitHubCommit
GitHubService --> GitHubContent
GitHubService --> GitHubFork
GitHubService --> GitHubIssue
GitHubService --> GitHubPullRequest
GitHubService --> GitHubRelease
GitHubService --> GitHubRepository
GitHubService --> GitHubSearchResult
GitHubService --> GitHubUser
GitHubService --> GitHubWorkflowRun
GitHubService --> GitHubWorkflowRunsResponse
GitHubService ..> GitHubWorkflowRunsResponse
GitHubService ..> PushFileResponse
```

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*