# Diagrammes de séquence

## `write`

```mermaid
sequenceDiagram
participant Caller
participant DocumentationWriterService
Caller->>DocumentationWriterService: write()
DocumentationWriterService-->>Caller: WriteResult
```

## `writeRefined`

```mermaid
sequenceDiagram
participant Caller
participant DocumentationWriterService
Caller->>DocumentationWriterService: writeRefined()
DocumentationWriterService-->>Caller: WriteResult
```

## `writeServiceDoc`

```mermaid
sequenceDiagram
participant Caller
participant DocumentationWriterService
Caller->>DocumentationWriterService: writeServiceDoc()
DocumentationWriterService-->>Caller: WriteResult
```

## `writeIndexDoc`

```mermaid
sequenceDiagram
participant Caller
participant DocumentationWriterService
Caller->>DocumentationWriterService: writeIndexDoc()
DocumentationWriterService-->>Caller: WriteResult
```

## `formatSuccessMessage`

```mermaid
sequenceDiagram
participant Caller
participant DocumentationWriterService
Caller->>DocumentationWriterService: formatSuccessMessage()
DocumentationWriterService-->>Caller: String
```

## `formatWarningMessage`

```mermaid
sequenceDiagram
participant Caller
participant DocumentationWriterService
Caller->>DocumentationWriterService: formatWarningMessage()
DocumentationWriterService-->>Caller: String
```

 ⓘ *(static-analysis)*

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*