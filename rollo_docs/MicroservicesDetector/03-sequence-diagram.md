# Diagrammes de séquence

## `getPattern`

```mermaid
sequenceDiagram
participant Caller
participant MicroservicesDetector
Caller->>MicroservicesDetector: getPattern()
MicroservicesDetector-->>Caller: ArchitecturePattern
```

## `score`

```mermaid
sequenceDiagram
participant Caller
participant MicroservicesDetector
Caller->>MicroservicesDetector: score()
MicroservicesDetector-->>Caller: PatternScore
```

 ⓘ *(static-analysis)*

---
