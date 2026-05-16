# Diagramme de classes

```mermaid
classDiagram
class ProjectAutoDetector {
  +run(ApplicationArguments args) void
}
ApplicationRunner <|-- ProjectAutoDetector
ProjectAutoDetector *-- ProjectContextService
ProjectAutoDetector *-- MemoryService
```

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*