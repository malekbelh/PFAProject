# API Publique

| Méthode | Paramètres | Retour | Async |
|---------|------------|--------|-------|
| `getFileTree` | `String owner, String repo, String branch` | `List<TreeEntry>` | Non |
| `snapshot` | `String owner, String repo, String branch` | `RepositorySnapshot` | Non |
| `fetchFileContent` | `String owner, String repo, String branch, String path` | `String` | Non |
| `allPaths` | `` | `List<String>` | Non |

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*