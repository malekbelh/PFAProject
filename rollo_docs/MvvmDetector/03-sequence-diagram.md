# Diagrammes de séquence

## `getPattern`

```mermaid
sequenceDiagram
participant Caller
participant MvvmDetector
Caller->>MvvmDetector: getPattern()
MvvmDetector-->>Caller: ArchitecturePattern
```

## `score`

```mermaid
sequenceDiagram
participant Caller
participant MvvmDetector
Caller->>MvvmDetector: score()
MvvmDetector-->>Caller: PatternScore
```

 ⓘ *(static-analysis)*

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*