# Diagramme de classes

```mermaid
classDiagram
class ComponentRoleResolver {
  +resolveRoles(List<String> paths, ArchitecturePattern pattern) List<ComponentRole>
}
ComponentRoleResolver --> ArchitecturalRole
ComponentRoleResolver --> ComponentRole
```

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*