# Diagramme de classes

```mermaid
classDiagram
class StackSignatureRegistry {
  +detectLanguages(List<String> paths) List<String>
  +detectBuildTools(List<String> paths) List<String>
  +detectFrameworks(Map<String, String> files) List<String>
}
```

---
