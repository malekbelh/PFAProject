# API Publique

| Méthode | Paramètres | Retour | Async |
|---------|------------|--------|-------|
| `getUserRepositories` | `String username` | `List<GitHubRepository>` | Non |
| `getAuthenticatedUserRepositories` | `` | `List<GitHubRepository>` | Non |
| `createRepository` | `String name, String description, boolean isPrivate` | `GitHubRepository` | Non |
| `deleteRepository` | `String username, String repo` | `void` | Non |
| `updateRepository` | `String username, String repo, String name, String description, boolean isPrivate` | `GitHubRepository` | Non |
| `getRepositoryCommits` | `String username, String repo, int limit` | `List<GitHubCommit>` | Non |
| `getRepositoryCommitsByPath` | `String username, String repo, String path, int limit` | `List<GitHubCommit>` | Non |
| `getLastCommit` | `String username, String repo` | `GitHubCommit` | Non |
| `getRepositoryCollaborators` | `String username, String repo` | `List<GitHubCollaborator>` | Non |
| `getRepositoryIssues` | `String username, String repo, String state, int limit` | `List<GitHubIssue>` | Non |
| `createIssue` | `String username, String repo, String title, String body` | `GitHubIssue` | Non |
| `getRepositoryPullRequests` | `String username, String repo, String state, int limit` | `List<GitHubPullRequest>` | Non |
| `createPullRequest` | `String username, String repo, String title, String head, String base, String body` | `GitHubPullRequest` | Non |
| `getRepositoryBranches` | `String username, String repo` | `List<GitHubBranch>` | Non |
| `createBranch` | `String username, String repo, String branchName, String fromBranch` | `GitHubBranch` | Non |
| `deleteBranch` | `String username, String repo, String branchName` | `void` | Non |
| `getUserProfile` | `String username` | `GitHubUser` | Non |
| `getAuthenticatedUserProfile` | `` | `GitHubUser` | Non |
| `getRepositoryReleases` | `String username, String repo, int limit` | `List<GitHubRelease>` | Non |
| `getLatestRelease` | `String username, String repo` | `GitHubRelease` | Non |
| `getWorkflowRuns` | `String username, String repo, int limit` | `List<GitHubWorkflowRun>` | Non |
| `getFileContent` | `String username, String repo, String path` | `GitHubContent` | Non |
| `pushFileContent` | `String username, String repo, String path, String content, String message, String branch` | `String` | Non |
| `deleteFile` | `String username, String repo, String path, String message, String branch` | `void` | Non |
| `searchRepositories` | `String query, int limit` | `List<GitHubRepository>` | Non |
| `getRepositoryForks` | `String username, String repo, int limit` | `List<GitHubFork>` | Non |
| `forkRepository` | `String username, String repo` | `GitHubRepository` | Non |
| `starRepository` | `String username, String repo` | `void` | Non |
| `unstarRepository` | `String username, String repo` | `void` | Non |
| `isRepositoryStarred` | `String username, String repo` | `boolean` | Non |
| `hasAuthentication` | `` | `boolean` | Non |
| `mergePullRequest` | `String username, String repo, int prNumber, String commitMessage` | `void` | Non |

---
