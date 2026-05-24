# API Publique

| Méthode | Paramètres | Retour | Async |
|---------|------------|--------|-------|
| `write` | `String projectPath, String chatSummary, String docBody, boolean detailed, String owner, String repo, String branch` | `WriteResult` | Non |
| `writeRefined` | `String projectPath, String reviewHeader, String refinedBody, String owner, String repo, String branch` | `WriteResult` | Non |
| `writeServiceDoc` | `String projectPath, String serviceName, Map<DocSection, List<DocFragment>> sections` | `WriteResult` | Non |
| `writeIndexDoc` | `String projectPath, List<DocFragment> fragments` | `WriteResult` | Non |
| `formatSuccessMessage` | `WriteResult.Success result, String owner, String repo, String branch` | `String` | Non |
| `formatWarningMessage` | `String reason` | `String` | Non |

---
