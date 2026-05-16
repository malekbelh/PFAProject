# Diagramme de classes

```mermaid
classDiagram
class DocumentationContextBuilder {
  +buildFingerprint(AnalysisResult analysis, List<ComponentGroup> groups) ProjectFingerprint
  +buildPromptContext(ProjectFingerprint fingerprint) String
}
DocumentationContextBuilder --> ComponentGroup
DocumentationContextBuilder --> ComponentRole
DocumentationContextBuilder --> ProjectFingerprint
```

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*