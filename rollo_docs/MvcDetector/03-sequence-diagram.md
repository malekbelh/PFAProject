# Diagrammes de séquence

## `getPattern`

```mermaid
sequenceDiagram
participant Caller
participant MvcDetector
Caller->>MvcDetector: getPattern()
MvcDetector-->>Caller: ArchitecturePattern
```

## `score`

```mermaid
sequenceDiagram
participant Caller
participant MvcDetector
Caller->>MvcDetector: score()
MvcDetector-->>Caller: PatternScore
```

 ⓘ *(static-analysis)*

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*