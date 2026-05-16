# Diagrammes de séquence

## `getUserRepositories`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getUserRepositories()
GitHubService-->>Caller: List<GitHubRepository>
```

## `getAuthenticatedUserRepositories`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getAuthenticatedUserRepositories()
GitHubService-->>Caller: List<GitHubRepository>
```

## `createRepository`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: createRepository()
GitHubService-->>Caller: GitHubRepository
```

## `deleteRepository`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: deleteRepository()
GitHubService-->>Caller: void
```

## `updateRepository`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: updateRepository()
GitHubService-->>Caller: GitHubRepository
```

## `getRepositoryCommits`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getRepositoryCommits()
GitHubService-->>Caller: List<GitHubCommit>
```

## `getRepositoryCommitsByPath`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getRepositoryCommitsByPath()
GitHubService-->>Caller: List<GitHubCommit>
```

## `getLastCommit`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getLastCommit()
GitHubService-->>Caller: GitHubCommit
```

## `getRepositoryCollaborators`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getRepositoryCollaborators()
GitHubService-->>Caller: List<GitHubCollaborator>
```

## `getRepositoryIssues`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getRepositoryIssues()
GitHubService-->>Caller: List<GitHubIssue>
```

## `createIssue`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: createIssue()
GitHubService-->>Caller: GitHubIssue
```

## `getRepositoryPullRequests`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getRepositoryPullRequests()
GitHubService-->>Caller: List<GitHubPullRequest>
```

## `createPullRequest`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: createPullRequest()
GitHubService-->>Caller: GitHubPullRequest
```

## `getRepositoryBranches`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getRepositoryBranches()
GitHubService-->>Caller: List<GitHubBranch>
```

## `createBranch`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: createBranch()
GitHubService-->>Caller: GitHubBranch
```

## `deleteBranch`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: deleteBranch()
GitHubService-->>Caller: void
```

## `getUserProfile`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getUserProfile()
GitHubService-->>Caller: GitHubUser
```

## `getAuthenticatedUserProfile`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getAuthenticatedUserProfile()
GitHubService-->>Caller: GitHubUser
```

## `getRepositoryReleases`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getRepositoryReleases()
GitHubService-->>Caller: List<GitHubRelease>
```

## `getLatestRelease`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getLatestRelease()
GitHubService-->>Caller: GitHubRelease
```

## `getWorkflowRuns`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getWorkflowRuns()
GitHubService-->>Caller: List<GitHubWorkflowRun>
```

## `getFileContent`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getFileContent()
GitHubService-->>Caller: GitHubContent
```

## `pushFileContent`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: pushFileContent()
GitHubService-->>Caller: String
```

## `deleteFile`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: deleteFile()
GitHubService-->>Caller: void
```

## `searchRepositories`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: searchRepositories()
GitHubService-->>Caller: List<GitHubRepository>
```

## `getRepositoryForks`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: getRepositoryForks()
GitHubService-->>Caller: List<GitHubFork>
```

## `forkRepository`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: forkRepository()
GitHubService-->>Caller: GitHubRepository
```

## `starRepository`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: starRepository()
GitHubService-->>Caller: void
```

## `unstarRepository`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: unstarRepository()
GitHubService-->>Caller: void
```

## `isRepositoryStarred`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: isRepositoryStarred()
GitHubService-->>Caller: boolean
```

## `hasAuthentication`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: hasAuthentication()
GitHubService-->>Caller: boolean
```

## `mergePullRequest`

```mermaid
sequenceDiagram
participant Caller
participant GitHubService
Caller->>GitHubService: mergePullRequest()
GitHubService-->>Caller: void
```

 ⓘ *(static-analysis)*

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*