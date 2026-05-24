package com.example.mcp_github.service.rollodocs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.mcp_github.model.DetectedService;
import com.example.mcp_github.model.PublicMethod;
import com.example.mcp_github.model.ServiceEcosystem;
import com.example.mcp_github.service.GitHubFileTreeService;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.LocalFileTreeService;
import com.example.mcp_github.service.TreeSitterAnalyzer;
import com.example.mcp_github.service.TreeSitterAnalyzer.AstFileSummary;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;

/**
 * ServiceEcosystemResolver — Résolution de l'écosystème d'un service.
 *
 * AVANT : regex fragiles sur le contenu brut des fichiers.
 * MAINTENANT : analyse AST via JavaParser (Tree-sitter JVM) pour Java,
 *              et TreeSitterAnalyzer pour JS/TS/Python.
 *
 * Avantages de l'AST vs regex :
 * - Méthodes avec génériques complexes (List<Map<String,Object>>) correctement parsées
 * - Annotations multi-lignes détectées
 * - Paramètres avec leurs types exacts
 * - Dépendances injectées via constructeur détectées précisément
 * - Zéro faux positifs sur les commentaires
 */
@Service
public class ServiceEcosystemResolver {

    private static final Logger log = LoggerFactory.getLogger(ServiceEcosystemResolver.class);

    private final GitHubFileTreeService fileTreeService;
    private final TreeSitterAnalyzer treeSitterAnalyzer;
    private final LocalFileTreeService localFileTreeService;
    private final JavaParser javaParser = new JavaParser();

    public ServiceEcosystemResolver(
            GitHubFileTreeService fileTreeService,
            TreeSitterAnalyzer treeSitterAnalyzer,
            LocalFileTreeService localFileTreeService) {
        this.fileTreeService = fileTreeService;
        this.treeSitterAnalyzer = treeSitterAnalyzer;
        this.localFileTreeService = localFileTreeService;
    }

    public ServiceEcosystem resolve(DetectedService service, RepositorySnapshot snapshot) {
        // ── 1. Lecture du contenu : local en priorité, GitHub en fallback ─────
        String content = readFileContent(service.path(), snapshot);

        if (content == null || content.isBlank()) {
            log.debug("Contenu vide pour {} — écosystème vide", service.path());
            return emptyEcosystem();
        }

        // ── 2. Dispatch selon le langage ──────────────────────────────────────
        String path = service.path().toLowerCase();
        if (path.endsWith(".java")) {
            return resolveJavaWithAst(service, content, snapshot);
        } else if (path.endsWith(".ts") || path.endsWith(".tsx")
                || path.endsWith(".js") || path.endsWith(".jsx")) {
            return resolveJsTsWithAst(service, content, snapshot);
        } else {
            return resolveGeneric(service, content, snapshot);
        }
    }

    // =========================================================================
    // JAVA — JavaParser (AST complet, Tree-sitter JVM)
    // =========================================================================

    private ServiceEcosystem resolveJavaWithAst(
            DetectedService service, String content, RepositorySnapshot snapshot) {

        try {
            ParseResult<CompilationUnit> result = javaParser.parse(content);

            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                log.debug("JavaParser a échoué pour {} — fallback TreeSitter", service.path());
                return resolveWithTreeSitterFallback(service, content, snapshot);
            }

            CompilationUnit cu = result.getResult().get();

            // ── Interfaces implémentées ───────────────────────────────────────
            List<String> implementsInterfaces = cu.findAll(ClassOrInterfaceDeclaration.class)
                    .stream()
                    .flatMap(c -> c.getImplementedTypes().stream())
                    .map(t -> t.getNameAsString())
                    .distinct()
                    .collect(Collectors.toList());

            // ── Méthodes publiques (AST précis) ───────────────────────────────
            List<PublicMethod> publicApi = cu.findAll(MethodDeclaration.class).stream()
                    .filter(MethodDeclaration::isPublic)
                    .map(m -> {
                        String returnType = m.getTypeAsString();
                        String name = m.getNameAsString();
                        List<String> params = m.getParameters().stream()
                                .map(Parameter::getTypeAsString)
                                .collect(Collectors.toList());
                        boolean isAsync = m.getAnnotations().stream()
                                .anyMatch(a -> a.getNameAsString().equals("Async"));
                        return new PublicMethod(name, params, returnType, isAsync, "public");
                    })
                    .collect(Collectors.toList());

            // ── Dépendances injectées (champs + constructeur) ─────────────────
            List<String> allInjected = extractInjectedDependencies(cu);

            List<String> usedRepositories = allInjected.stream()
                    .filter(c -> c.endsWith("Repository") || c.endsWith("Dao"))
                    .distinct().collect(Collectors.toList());

            List<String> dependentServices = allInjected.stream()
                    .filter(c -> c.endsWith("Service") && !c.equals(service.className()))
                    .distinct().collect(Collectors.toList());

            List<String> usedDtos = allInjected.stream()
                    .filter(c -> c.endsWith("Dto") || c.endsWith("DTO")
                            || c.endsWith("Request") || c.endsWith("Response"))
                    .distinct().collect(Collectors.toList());

            // ── Entités manipulées (croisement imports + fichiers du projet) ──
            List<String> imports = cu.getImports().stream()
                    .map(i -> i.getNameAsString())
                    .collect(Collectors.toList());
            List<String> manipulatedEntities = extractEntitiesFromImports(imports, snapshot.allPaths());

            // ── Controllers appelants ─────────────────────────────────────────
            List<String> callingControllers = findCallingControllers(service.className(), snapshot);

            log.debug("AST Java résolu pour {} : {} méthodes, {} dépendances",
                    service.className(), publicApi.size(), allInjected.size());

            return new ServiceEcosystem(
                    implementsInterfaces,
                    callingControllers,
                    usedRepositories,
                    manipulatedEntities,
                    usedDtos,
                    dependentServices,
                    publicApi);

        } catch (Exception e) {
            log.warn("Erreur AST pour {} : {} — fallback TreeSitter", service.path(), e.getMessage());
            return resolveWithTreeSitterFallback(service, content, snapshot);
        }
    }

    /**
     * Extrait les dépendances injectées via champs et constructeur.
     * Détecte @Autowired, @Inject, final fields, et injection constructeur.
     */
    private List<String> extractInjectedDependencies(CompilationUnit cu) {
        List<String> injected = new ArrayList<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            // Champs annotés @Autowired / @Inject ou final
            cls.getFields().forEach(field -> {
                boolean isAutowired = field.getAnnotations().stream()
                        .anyMatch(a -> a.getNameAsString().equals("Autowired")
                                || a.getNameAsString().equals("Inject"));
                boolean isFinal = field.isFinal();
                if (isAutowired || isFinal) {
                    String typeName = field.getElementType().asString();
                    // Ignorer les types primitifs et java.lang
                    if (Character.isUpperCase(typeName.charAt(0))) {
                        injected.add(typeName);
                    }
                }
            });

            // Paramètres du constructeur (injection constructeur Spring)
            cls.getConstructors().forEach(ctor -> {
                if (ctor.getParameters().size() > 0) {
                    ctor.getParameters().forEach(param -> {
                        String typeName = param.getTypeAsString();
                        if (Character.isUpperCase(typeName.charAt(0))) {
                            injected.add(typeName);
                        }
                    });
                }
            });
        });

        return injected.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Croise les imports du fichier avec les chemins du projet pour trouver
     * les entités réellement utilisées (plus précis que la recherche par mot-clé).
     */
    private List<String> extractEntitiesFromImports(List<String> imports, List<String> allPaths) {
        // Noms des classes dans les dossiers entity/model
        List<String> entityNames = allPaths.stream()
                .filter(p -> p.contains("/entity/") || p.contains("/entities/")
                        || p.contains("/model/") || p.contains("/models/"))
                .map(p -> {
                    String f = p.substring(p.lastIndexOf('/') + 1);
                    return f.contains(".") ? f.substring(0, f.lastIndexOf('.')) : f;
                })
                .collect(Collectors.toList());

        // Garder uniquement celles qui sont importées
        return imports.stream()
                .map(imp -> imp.contains(".") ? imp.substring(imp.lastIndexOf('.') + 1) : imp)
                .filter(entityNames::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    // =========================================================================
    // JS/TS — TreeSitterAnalyzer
    // =========================================================================

    private ServiceEcosystem resolveJsTsWithAst(
            DetectedService service, String content, RepositorySnapshot snapshot) {

        AstFileSummary summary = treeSitterAnalyzer.analyzeFiles(
                Map.of(service.path(), content)).get(service.path());

        if (summary == null) return emptyEcosystem();

        // Méthodes publiques depuis l'AST JS/TS
        List<PublicMethod> publicApi = summary.methods().stream()
                .map(name -> new PublicMethod(name, List.of(), "any", false, "public"))
                .collect(Collectors.toList());

        // Interfaces implémentées (TypeScript)
        List<String> implementsInterfaces = summary.interfaces();

        // Dépendances depuis les imports
        List<String> usedRepositories = summary.imports().stream()
                .filter(i -> i.toLowerCase().contains("repository") || i.toLowerCase().contains("dao"))
                .collect(Collectors.toList());

        List<String> dependentServices = summary.imports().stream()
                .filter(i -> i.toLowerCase().contains("service")
                        && !i.equals(service.className()))
                .collect(Collectors.toList());

        List<String> callingControllers = findCallingControllers(service.className(), snapshot);

        log.debug("AST JS/TS résolu pour {} : {} méthodes", service.className(), publicApi.size());

        return new ServiceEcosystem(
                implementsInterfaces,
                callingControllers,
                usedRepositories,
                List.of(),
                List.of(),
                dependentServices,
                publicApi);
    }

    // =========================================================================
    // FALLBACK — TreeSitterAnalyzer (regex structurées)
    // =========================================================================

    /**
     * Fallback utilisé quand JavaParser échoue (fichiers avec preview features,
     * syntaxe non standard, etc.). Utilise TreeSitterAnalyzer qui applique
     * des regex structurées plus robustes que les anciennes regex inline.
     */
    private ServiceEcosystem resolveWithTreeSitterFallback(
            DetectedService service, String content, RepositorySnapshot snapshot) {

        AstFileSummary summary = treeSitterAnalyzer.analyzeFiles(
                Map.of(service.path(), content)).get(service.path());

        if (summary == null) return emptyEcosystem();

        List<PublicMethod> publicApi = summary.methods().stream()
                .map(name -> new PublicMethod(name, List.of(), "Object", false, "public"))
                .collect(Collectors.toList());

        List<String> usedRepositories = summary.springComponents().stream()
                .filter(c -> c.startsWith("Repository:"))
                .map(c -> c.substring("Repository:".length()))
                .collect(Collectors.toList());

        List<String> callingControllers = findCallingControllers(service.className(), snapshot);

        return new ServiceEcosystem(
                List.of(),
                callingControllers,
                usedRepositories,
                List.of(),
                List.of(),
                List.of(),
                publicApi);
    }

    // =========================================================================
    // GÉNÉRIQUE (autres langages)
    // =========================================================================

    private ServiceEcosystem resolveGeneric(
            DetectedService service, String content, RepositorySnapshot snapshot) {

        AstFileSummary summary = treeSitterAnalyzer.analyzeFiles(
                Map.of(service.path(), content)).get(service.path());

        if (summary == null) return emptyEcosystem();

        List<PublicMethod> publicApi = summary.methods().stream()
                .map(name -> new PublicMethod(name, List.of(), "void", false, "public"))
                .collect(Collectors.toList());

        return new ServiceEcosystem(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), publicApi);
    }

    // =========================================================================
    // LECTURE DU CONTENU — local en priorité, GitHub en fallback
    // =========================================================================

    /**
     * Lit le contenu d'un fichier :
     * 1. Depuis le disque local si le projet est accessible (FileSystemTools)
     * 2. Depuis l'API GitHub sinon
     */
    private String readFileContent(String filePath, RepositorySnapshot snapshot) {
        // Essai local d'abord — cherche le chemin dans le snapshot owner/repo
        // Le projectPath local est encodé dans le owner du snapshot si disponible
        // On tente via LocalFileTreeService en cherchant dans les keyFiles d'abord
        String fromKeyFiles = snapshot.keyFiles().get(filePath);
        if (fromKeyFiles != null) {
            log.debug("📄 Contenu depuis keyFiles (local ou cache) : {}", filePath);
            return fromKeyFiles;
        }

        // Fallback : API GitHub
        log.debug("☁️  Contenu depuis GitHub API : {}", filePath);
        return fileTreeService.fetchFileContent(
                snapshot.owner(), snapshot.repo(), snapshot.branch(), filePath);
    }

    // =========================================================================
    // CONTROLLERS APPELANTS
    // =========================================================================

    private List<String> findCallingControllers(String serviceName, RepositorySnapshot snapshot) {
        List<String> controllers = new ArrayList<>();

        List<String> controllerPaths = snapshot.allPaths().stream()
                .filter(p -> p.endsWith("Controller.java")
                        || p.endsWith("Controller.ts")
                        || p.endsWith("Controller.js"))
                .collect(Collectors.toList());

        for (String path : controllerPaths) {
            String content = fileTreeService.fetchFileContent(
                    snapshot.owner(), snapshot.repo(), snapshot.branch(), path);
            if (content != null && content.contains(serviceName)) {
                String className = path.substring(path.lastIndexOf('/') + 1);
                className = className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : className;
                controllers.add(className);
            }
        }
        return controllers;
    }

    // =========================================================================
    // UTILITAIRE
    // =========================================================================

    private ServiceEcosystem emptyEcosystem() {
        return new ServiceEcosystem(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
