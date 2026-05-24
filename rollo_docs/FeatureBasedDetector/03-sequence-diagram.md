# Diagrammes de séquence

## `getPattern`

```mermaid
sequenceDiagram
participant Caller
participant FeatureBasedDetector
Caller->>FeatureBasedDetector: getPattern()
FeatureBasedDetector-->>Caller: ArchitecturePattern
```

## `score`

```mermaid
sequenceDiagram
participant Caller
participant FeatureBasedDetector
Caller->>FeatureBasedDetector: score()
FeatureBasedDetector-->>Caller: PatternScore
```

 ⓘ *(static-analysis)*

---
