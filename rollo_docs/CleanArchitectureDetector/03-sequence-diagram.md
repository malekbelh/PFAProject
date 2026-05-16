# Diagrammes de séquence

## `getPattern`

```mermaid
sequenceDiagram
participant Caller
participant CleanArchitectureDetector
Caller->>CleanArchitectureDetector: getPattern()
CleanArchitectureDetector-->>Caller: ArchitecturePattern
```

## `score`

```mermaid
sequenceDiagram
participant Caller
participant CleanArchitectureDetector
Caller->>CleanArchitectureDetector: score()
CleanArchitectureDetector-->>Caller: PatternScore
```

 ⓘ *(static-analysis)*

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*