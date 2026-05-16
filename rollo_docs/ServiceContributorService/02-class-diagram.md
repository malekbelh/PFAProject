# Diagramme de classes

```mermaid
classDiagram
class ServiceContributorService {
  +fetchContributions(DetectedService service, RepositorySnapshot snapshot) List<ContributorStats>
  +fetchCommits(DetectedService service, RepositorySnapshot snapshot) List<GitHubCommit>
}
ServiceContributorService *-- GitHubService
ServiceContributorService *-- DetectedService
ServiceContributorService --> ContributorStats
ServiceContributorService --> DetectedService
ServiceContributorService --> GitHubCommit
```

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*