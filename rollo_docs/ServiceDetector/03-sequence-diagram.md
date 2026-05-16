# Diagrammes de séquence

## `detect`

```mermaid
sequenceDiagram
participant Caller
participant ServiceDetector
Caller->>ServiceDetector: detect()
ServiceDetector-->>Caller: List<DetectedService>
```

 ⓘ *(static-analysis)*

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*