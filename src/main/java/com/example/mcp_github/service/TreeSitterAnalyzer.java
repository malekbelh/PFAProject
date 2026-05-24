package com.example.mcp_github.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

/**
 * TreeSitterAnalyzer — Analyse AST du contenu des fichiers source.
 *
 * Remplace l'analyse purement heuristique (noms de dossiers/fichiers) par une
 * analyse du contenu réel des fichiers :
 * - Java  : via JavaParser (AST complet — équivalent JVM de Tree-sitter)
 * - JS/TS : via regex structurées (extraction de classes, fonctions, exports)
 *
 * Intégré dans ProjectStructureAnalyzer pour enrichir l'AnalysisResult avec
 * des données concrètes extraites du code source.
 */
@Service
public class TreeSitterAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterAnalyzer.class);

    private final JavaParser javaParser = new JavaParser();

    // ─── Patterns JS/TS ──────────────────────────────────────────────────────
    private static final Pattern JS_CLASS      = Pattern.compile("(?:export\\s+)?(?:abstract\\s+)?class\\s+(\\w+)");
    private static final Pattern JS_FUNCTION   = Pattern.compile("(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)");
    private static final Pattern JS_ARROW      = Pattern.compile("(?:export\\s+)?(?:const|let)\\s+(\\w+)\\s*=\\s*(?:async\\s+)?\\(");
    private static final Pattern JS_INTERFACE  = Pattern.compile("(?:export\\s+)?interface\\s+(\\w+)");
    private static final Pattern JS_ANNOTATION = Pattern.compile("@(\\w+)(?:\\(|\\s)");
    private static final Pattern JS_IMPORT     = Pattern.compile("import\\s+.*?from\\s+['\"]([^'\"]+)['\"]");

    // ─── Patterns Python ─────────────────────────────────────────────────────
    private static final Pattern PY_CLASS    = Pattern.compile("^class\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern PY_FUNCTION = Pattern.compile("^def\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern PY_IMPORT   = Pattern.compile("^(?:import|from)\\s+(\\S+)", Pattern.MULTILINE);

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Analyse un ensemble de fichiers source et retourne un résumé AST par fichier.
     *
     * @param fileContents map path → contenu brut du fichier
     * @return map path → AstFileSummary
     */
    public Map<String, AstFileSummary> analyzeFiles(Map<String, String> fileContents) {
        Map<String, AstFileSummary> results = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            if (content == null || content.isBlank()) continue;
            try {
                AstFileSummary summary = analyzeFile(path, content);
                if (summary != null) results.put(path, summary);
            } catch (Exception e) {
                log.debug("AST parse skipped for {}: {}", path, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Produit un rapport textuel enrichi à partir des résumés AST,
     * utilisable directement dans les prompts LLM.
     */
    public String buildAstReport(Map<String, AstFileSummary> summaries) {
        if (summaries.isEmpty()) return "Aucune donnée AST disponible.";

        StringBuilder sb = new StringBuilder();
        sb.append("## Analyse AST des fichiers source\n\n");

        for (Map.Entry<String, AstFileSummary> entry : summaries.entrySet()) {
            AstFileSummary s = entry.getValue();
            sb.append("### `").append(entry.getKey()).append("`\n");
            sb.append("- **Langage :** ").append(s.language()).append("\n");

            if (!s.classes().isEmpty()) {
                sb.append("- **Classes :** ").append(String.join(", ", s.classes())).append("\n");
            }
            if (!s.interfaces().isEmpty()) {
                sb.append("- **Interfaces :** ").append(String.join(", ", s.interfaces())).append("\n");
            }
            if (!s.methods().isEmpty()) {
                sb.append("- **Méthodes publiques :** ").append(String.join(", ", s.methods())).append("\n");
            }
            if (!s.annotations().isEmpty()) {
                sb.append("- **Annotations :** ").append(String.join(", ", s.annotations())).append("\n");
            }
            if (!s.imports().isEmpty()) {
                sb.append("- **Imports clés :** ").append(String.join(", ", s.imports().stream().limit(5).toList())).append("\n");
            }
            if (!s.springComponents().isEmpty()) {
                sb.append("- **Composants Spring :** ").append(String.join(", ", s.springComponents())).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Extrait les signaux architecturaux concrets depuis les résumés AST.
     * Ces signaux enrichissent la détection de pattern.
     */
    public List<String> extractArchitecturalSignals(Map<String, AstFileSummary> summaries) {
        List<String> signals = new ArrayList<>();
        long controllers = summaries.values().stream()
                .filter(s -> s.annotations().contains("RestController") || s.annotations().contains("Controller"))
                .count();
        long services = summaries.values().stream()
                .filter(s -> s.annotations().contains("Service"))
                .count();
        long repositories = summaries.values().stream()
                .filter(s -> s.annotations().contains("Repository"))
                .count();
        long components = summaries.values().stream()
                .filter(s -> s.annotations().contains("Component"))
                .count();
        long tools = summaries.values().stream()
                .filter(s -> s.annotations().contains("Tool") || !s.springComponents().isEmpty())
                .count();

        if (controllers > 0) signals.add("@RestController/@Controller détecté (" + controllers + " fichiers)");
        if (services > 0)     signals.add("@Service détecté (" + services + " fichiers)");
        if (repositories > 0) signals.add("@Repository détecté (" + repositories + " fichiers)");
        if (components > 0)   signals.add("@Component détecté (" + components + " fichiers)");
        if (tools > 0)        signals.add("@Tool MCP détecté (" + tools + " fichiers)");

        // Détection Spring AI MCP
        boolean hasMcpTool = summaries.values().stream()
                .anyMatch(s -> s.imports().stream().anyMatch(i -> i.contains("spring.ai.tool")));
        if (hasMcpTool) signals.add("Spring AI @Tool annotations présentes — MCP Server confirmé");

        // Détection interfaces Repository (Spring Data)
        boolean hasSpringData = summaries.values().stream()
                .anyMatch(s -> s.imports().stream().anyMatch(i -> i.contains("springframework.data")));
        if (hasSpringData) signals.add("Spring Data détecté — couche persistance confirmée");

        return signals;
    }

    // =========================================================================
    // DISPATCH PAR LANGAGE
    // =========================================================================

    private AstFileSummary analyzeFile(String path, String content) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".java"))                    return analyzeJava(path, content);
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return analyzeTypeScript(path, content);
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return analyzeJavaScript(path, content);
        if (lower.endsWith(".py"))                      return analyzePython(path, content);
        return null;
    }

    // =========================================================================
    // JAVA — JavaParser (AST complet)
    // =========================================================================

    private AstFileSummary analyzeJava(String path, String content) {
        List<String> classes      = new ArrayList<>();
        List<String> interfaces   = new ArrayList<>();
        List<String> methods      = new ArrayList<>();
        List<String> annotations  = new ArrayList<>();
        List<String> imports      = new ArrayList<>();
        List<String> springComps  = new ArrayList<>();

        try {
            ParseResult<CompilationUnit> result = javaParser.parse(content);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                // Fallback regex si JavaParser échoue (ex: preview features)
                return analyzeJavaFallback(path, content);
            }

            CompilationUnit cu = result.getResult().get();

            // Imports
            cu.getImports().forEach(imp -> imports.add(imp.getNameAsString()));

            // Classes & interfaces
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(decl -> {
                if (decl.isInterface()) {
                    interfaces.add(decl.getNameAsString());
                } else {
                    classes.add(decl.getNameAsString());
                }

                // Annotations sur la classe
                decl.getAnnotations().forEach(ann -> {
                    String name = ann.getNameAsString();
                    annotations.add(name);
                    if (isSpringComponent(name)) springComps.add(name + ":" + decl.getNameAsString());
                });

                // Méthodes publiques
                decl.getMethods().stream()
                        .filter(m -> m.isPublic())
                        .forEach(m -> {
                            methods.add(m.getNameAsString());
                            m.getAnnotations().forEach(ann -> annotations.add(ann.getNameAsString()));
                        });
            });

        } catch (Exception e) {
            return analyzeJavaFallback(path, content);
        }

        return new AstFileSummary("Java", classes, interfaces, methods,
                deduplicate(annotations), imports, springComps);
    }

    /** Fallback regex pour les fichiers Java non parsables par JavaParser */
    private AstFileSummary analyzeJavaFallback(String path, String content) {
        List<String> classes     = extractMatches(content, Pattern.compile("(?:public\\s+)?(?:abstract\\s+)?class\\s+(\\w+)"));
        List<String> interfaces  = extractMatches(content, Pattern.compile("(?:public\\s+)?interface\\s+(\\w+)"));
        List<String> methods     = extractMatches(content, Pattern.compile("public\\s+\\w+\\s+(\\w+)\\s*\\("));
        List<String> annotations = extractMatches(content, Pattern.compile("@(\\w+)"));
        List<String> imports     = extractMatches(content, Pattern.compile("import\\s+([\\w.]+);"));
        List<String> springComps = annotations.stream()
                .filter(this::isSpringComponent)
                .map(a -> a + ":" + (classes.isEmpty() ? "?" : classes.get(0)))
                .toList();
        return new AstFileSummary("Java", classes, interfaces, methods,
                deduplicate(annotations), imports, springComps);
    }

    // =========================================================================
    // TYPESCRIPT / JAVASCRIPT — Regex structurées
    // =========================================================================

    private AstFileSummary analyzeTypeScript(String path, String content) {
        return analyzeJsTs(path, content, "TypeScript");
    }

    private AstFileSummary analyzeJavaScript(String path, String content) {
        return analyzeJsTs(path, content, "JavaScript");
    }

    private AstFileSummary analyzeJsTs(String path, String content, String lang) {
        List<String> classes     = extractMatches(content, JS_CLASS);
        List<String> interfaces  = extractMatches(content, JS_INTERFACE);
        List<String> methods     = new ArrayList<>();
        methods.addAll(extractMatches(content, JS_FUNCTION));
        methods.addAll(extractMatches(content, JS_ARROW));
        List<String> annotations = extractMatches(content, JS_ANNOTATION);
        List<String> imports     = extractMatches(content, JS_IMPORT);
        return new AstFileSummary(lang, classes, interfaces, methods,
                deduplicate(annotations), imports, List.of());
    }

    // =========================================================================
    // PYTHON — Regex structurées
    // =========================================================================

    private AstFileSummary analyzePython(String path, String content) {
        List<String> classes  = extractMatches(content, PY_CLASS);
        List<String> methods  = extractMatches(content, PY_FUNCTION);
        List<String> imports  = extractMatches(content, PY_IMPORT);
        return new AstFileSummary("Python", classes, List.of(), methods,
                List.of(), imports, List.of());
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

    private List<String> extractMatches(String content, Pattern pattern) {
        List<String> results = new ArrayList<>();
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            results.add(m.group(1));
        }
        return results;
    }

    private List<String> deduplicate(List<String> list) {
        return list.stream().distinct().toList();
    }

    private boolean isSpringComponent(String annotation) {
        return switch (annotation) {
            case "Component", "Service", "Repository", "Controller",
                 "RestController", "Configuration", "Bean",
                 "SpringBootApplication", "Tool" -> true;
            default -> false;
        };
    }

    // =========================================================================
    // MODÈLE DE RÉSULTAT
    // =========================================================================

    /**
     * Résumé AST d'un fichier source unique.
     *
     * @param language      Langage détecté
     * @param classes       Noms des classes déclarées
     * @param interfaces    Noms des interfaces déclarées
     * @param methods       Noms des méthodes publiques
     * @param annotations   Annotations présentes (dédupliquées)
     * @param imports       Imports déclarés
     * @param springComponents Composants Spring détectés (annotation:className)
     */
    public record AstFileSummary(
            String language,
            List<String> classes,
            List<String> interfaces,
            List<String> methods,
            List<String> annotations,
            List<String> imports,
            List<String> springComponents) {
    }
}
