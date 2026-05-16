# Diagrammes de séquence

## `generate`

```mermaid
sequenceDiagram
participant Caller
participant RolloDocsIndexGenerator
Caller->>RolloDocsIndexGenerator: generate()
RolloDocsIndexGenerator-->>Caller: List<DocFragment>
```

 ⓘ *(static-analysis)*

---
*Généré par Antigravity MCP. Ne pas éditer manuellement.*