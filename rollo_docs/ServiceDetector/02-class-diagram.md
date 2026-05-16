# Diagramme de classes

```mermaid
classDiagram
class ServiceDetector {
  +detect(RepositorySnapshot snapshot) List<DetectedService>
}
ServiceDetector --> DetectedService
ServiceDetector --> DetectionReason
```

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*