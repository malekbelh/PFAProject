# Diagramme de classes

```mermaid
classDiagram
class DocumentationWriterService {
  +write(String projectPath, String chatSummary, String docBody, boolean detailed, String owner, String repo, String branch) WriteResult
  +writeRefined(String projectPath, String reviewHeader, String refinedBody, String owner, String repo, String branch) WriteResult
  +writeServiceDoc(String projectPath, String serviceName, Map<DocSection, List<DocFragment>> sections) WriteResult
  +writeIndexDoc(String projectPath, List<DocFragment> fragments) WriteResult
  +formatSuccessMessage(WriteResult.Success result, String owner, String repo, String branch) String
  +formatWarningMessage(String reason) String
}
WriteResult <|-- DocumentationWriterService
DocumentationWriterService --> ComponentGroup
DocumentationWriterService --> DocFragment
DocumentationWriterService --> DocSection
```

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*