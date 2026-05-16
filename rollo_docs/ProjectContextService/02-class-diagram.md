# Diagramme de classes

```mermaid
classDiagram
class ProjectContextService {
  +detectFromCurrentDirectory() Optional<GitProjectContext>
  +detectFromDirectory(String path) Optional<GitProjectContext>
}
```

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*