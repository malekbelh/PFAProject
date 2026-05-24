package com.example.mcp_github.service.rollodocs.generators;

import java.util.List;
import java.util.Map;

import com.example.mcp_github.model.ContributorStats;
import com.example.mcp_github.model.DetectedService;
import com.example.mcp_github.model.DocFragment;
import com.example.mcp_github.model.DocSection;
import com.example.mcp_github.model.GitHubCommit;
import com.example.mcp_github.model.ProvenanceLevel;
import com.example.mcp_github.model.PublicMethod;
import com.example.mcp_github.model.ServiceEcosystem;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.TreeSitterAnalyzer.AstFileSummary;

/**
 * SectionGenerators — Génération des sections Markdown de documentation.
 *
 * Enrichi avec les données AST (TreeSitterAnalyzer / JavaParser) :
 * - Les diagrammes de classes utilisent les vraies méthodes extraites par AST
 * - L'API publique affiche les types de paramètres réels
 * - Les annotations Spring sont affichées dans la description
 * - La provenance (OBSERVED/INFERRED/PLACEHOLDER) reflète la qualité AST
 */
public class SectionGenerators {

    // =========================================================================
    // DESCRIPTION
    // =========================================================================

    public static List<DocFragment> generateDescription(DetectedService service) {
        return generateDescription(service, null);
    }

    public static List<DocFragment> generateDescription(DetectedService service, AstFileSummary astSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Description de `").append(service.className()).append("`\n\n");
        sb.append("- **Package** : `").append(service.packageName()).append("`\n");
        sb.append("- **Raison de détection** : `").append(service.reason()).append("`\n");

        // ── Données AST enrichies ─────────────────────────────────────────────
        if (astSummary != null) {
            sb.append("- **Langage** : `").append(astSummary.language()).append("`\n");

            if (!astSummary.annotations().isEmpty()) {
                sb.append("- **Annotations** : ");
                astSummary.annotations().forEach(a -> sb.append("`@").append(a).append("` "));
                sb.append("\n");
            }

            if (!astSummary.springComponents().isEmpty()) {
                sb.append("- **Rôle Spring** : ");
                astSummary.springComponents().forEach(c -> {
                    String[] parts = c.split(":");
                    sb.append("`@").append(parts[0]).append("` ");
                });
                sb.append("\n");
            }

            if (!astSummary.classes().isEmpty()) {
                sb.append("- **Classes déclarées** : ")
                        .append(String.join(", ", astSummary.classes().stream()
                                .map(c -> "`" + c + "`").toList()))
                        .append("\n");
            }

            if (!astSummary.interfaces().isEmpty()) {
                sb.append("- **Interfaces déclarées** : ")
                        .append(String.join(", ", astSummary.interfaces().stream()
                                .map(i -> "`" + i + "`").toList()))
                        .append("\n");
            }
        }

        // ── Données écosystème ────────────────────────────────────────────────
        ServiceEcosystem eco = service.ecosystem();
        if (eco != null) {
            if (!eco.implementsInterfaces().isEmpty()) {
                sb.append("- **Interfaces implémentées** : ")
                        .append(String.join(", ", eco.implementsInterfaces())).append("\n");
            }
            sb.append("- **Méthodes publiques** : ").append(eco.publicApi().size()).append("\n");
            int deps = eco.dependentServices().size() + eco.usedRepositories().size();
            sb.append("- **Dépendances injectées** : ").append(deps).append("\n");
        }

        ProvenanceLevel level = astSummary != null ? ProvenanceLevel.OBSERVED : ProvenanceLevel.INFERRED;
        return List.of(new DocFragment(sb.toString(), level, "tree-sitter-ast"));
    }

    // =========================================================================
    // DIAGRAMME DE CLASSES
    // =========================================================================

    public static List<DocFragment> generateClassDiagram(DetectedService service) {
        return generateClassDiagram(service, null);
    }

    public static List<DocFragment> generateClassDiagram(DetectedService service, AstFileSummary astSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Diagramme de classes\n\n```mermaid\nclassDiagram\n");

        ServiceEcosystem eco = service.ecosystem();

        // ── Corps de la classe avec méthodes AST ──────────────────────────────
        sb.append("class ").append(service.className()).append(" {\n");

        if (eco != null && !eco.publicApi().isEmpty()) {
            // Méthodes extraites par AST (types précis)
            for (PublicMethod pm : eco.publicApi()) {
                String params = String.join(", ", pm.parameterTypes());
                sb.append("  +").append(pm.name())
                        .append("(").append(params).append(")")
                        .append(" ").append(pm.returnType());
                if (pm.isAsync()) sb.append(" <<async>>");
                sb.append("\n");
            }
        } else if (astSummary != null && !astSummary.methods().isEmpty()) {
            // Fallback : méthodes depuis AstFileSummary (sans types de retour)
            astSummary.methods().stream().limit(10)
                    .forEach(m -> sb.append("  +").append(m).append("()\n"));
        }

        // Annotations comme stéréotypes Mermaid
        if (astSummary != null) {
            astSummary.springComponents().stream()
                    .filter(c -> c.contains(":"))
                    .map(c -> c.split(":")[0])
                    .findFirst()
                    .ifPresent(ann -> sb.append("  <<").append(ann).append(">>\n"));
        }

        sb.append("}\n");

        // ── Relations ─────────────────────────────────────────────────────────
        if (eco != null) {
            for (String intf : eco.implementsInterfaces()) {
                sb.append(intf).append(" <|-- ").append(service.className()).append("\n");
            }
            for (String repo : eco.usedRepositories()) {
                sb.append(service.className()).append(" *-- ").append(repo)
                        .append(" : injecte\n");
            }
            for (String srv : eco.dependentServices()) {
                sb.append(service.className()).append(" *-- ").append(srv)
                        .append(" : utilise\n");
            }
            for (String entity : eco.manipulatedEntities()) {
                sb.append(service.className()).append(" --> ").append(entity)
                        .append(" : manipule\n");
            }
            for (String dto : eco.usedDtos()) {
                sb.append(service.className()).append(" ..> ").append(dto)
                        .append(" : DTO\n");
            }
        }

        sb.append("```\n");

        boolean hasRealData = (eco != null && !eco.publicApi().isEmpty())
                || (astSummary != null && !astSummary.methods().isEmpty());
        ProvenanceLevel level = hasRealData ? ProvenanceLevel.OBSERVED : ProvenanceLevel.INFERRED;

        return List.of(new DocFragment(sb.toString(), level, "tree-sitter-ast"));
    }

    // =========================================================================
    // DIAGRAMME DE SÉQUENCE
    // =========================================================================

    public static List<DocFragment> generateSequenceDiagram(DetectedService service) {
        return generateSequenceDiagram(service, null);
    }

    public static List<DocFragment> generateSequenceDiagram(DetectedService service, AstFileSummary astSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Diagrammes de séquence\n\n");

        ServiceEcosystem eco = service.ecosystem();

        if (eco == null || eco.publicApi().isEmpty()) {
            // Fallback sur les méthodes AST si disponibles
            if (astSummary != null && !astSummary.methods().isEmpty()) {
                sb.append("## Méthodes détectées par AST\n\n");
                sb.append("```mermaid\nsequenceDiagram\n");
                sb.append("participant Caller\n");
                sb.append("participant ").append(service.className()).append("\n");
                astSummary.methods().stream().limit(3).forEach(m -> {
                    sb.append("Caller->>").append(service.className()).append(": ").append(m).append("()\n");
                    sb.append(service.className()).append("-->>Caller: result\n");
                });
                sb.append("```\n");
                return List.of(new DocFragment(sb.toString(), ProvenanceLevel.INFERRED, "tree-sitter-ast"));
            }

            sb.append("> [!NOTE]\n> Diagrammes non disponibles (aucune méthode publique détectée).\n");
            return List.of(new DocFragment(sb.toString(), ProvenanceLevel.PLACEHOLDER, "tree-sitter-ast"));
        }

        // Diagramme par méthode publique (données AST précises)
        for (PublicMethod pm : eco.publicApi()) {
            sb.append("## `").append(pm.name()).append("`\n\n");
            sb.append("```mermaid\nsequenceDiagram\n");
            sb.append("participant Caller\n");
            sb.append("participant ").append(service.className()).append("\n");

            // Appel avec paramètres réels
            String paramStr = pm.parameterTypes().isEmpty() ? ""
                    : pm.parameterTypes().stream().limit(2).collect(java.util.stream.Collectors.joining(", "));
            sb.append("Caller->>").append(service.className())
                    .append(": ").append(pm.name()).append("(").append(paramStr).append(")\n");

            // Appels aux repositories si présents
            if (eco.usedRepositories() != null && !eco.usedRepositories().isEmpty()) {
                String repo = eco.usedRepositories().get(0);
                sb.append(service.className()).append("->>").append(repo).append(": query()\n");
                sb.append(repo).append("-->>").append(service.className()).append(": data\n");
            }

            // Appels aux services dépendants
            if (eco.dependentServices() != null && !eco.dependentServices().isEmpty()) {
                String dep = eco.dependentServices().get(0);
                sb.append(service.className()).append("->>").append(dep).append(": delegate()\n");
                sb.append(dep).append("-->>").append(service.className()).append(": result\n");
            }

            sb.append(service.className()).append("-->>Caller: ")
                    .append(pm.returnType()).append("\n");
            sb.append("```\n\n");
        }

        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "tree-sitter-ast"));
    }

    // =========================================================================
    // COMPOSANTS
    // =========================================================================

    public static List<DocFragment> generateComponentsList(DetectedService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Composants de l'écosystème\n\n");
        sb.append("| Nom | Type | Source |\n");
        sb.append("|---|---|---|\n");

        sb.append("| `").append(service.className()).append("` | Service | AST |\n");

        ServiceEcosystem eco = service.ecosystem();
        if (eco != null) {
            eco.implementsInterfaces().forEach(i ->
                    sb.append("| `").append(i).append("` | Interface | AST |\n"));
            eco.callingControllers().forEach(c ->
                    sb.append("| `").append(c).append("` | Controller | AST |\n"));
            eco.usedRepositories().forEach(r ->
                    sb.append("| `").append(r).append("` | Repository | AST |\n"));
            eco.manipulatedEntities().forEach(e ->
                    sb.append("| `").append(e).append("` | Entity | AST |\n"));
            eco.usedDtos().forEach(d ->
                    sb.append("| `").append(d).append("` | DTO | AST |\n"));
            eco.dependentServices().forEach(s ->
                    sb.append("| `").append(s).append("` | Service Dépendant | AST |\n"));
        }

        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "tree-sitter-ast"));
    }

    // =========================================================================
    // DÉPENDANCES
    // =========================================================================

    public static List<DocFragment> generateDependencies(DetectedService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Dépendances\n\n");
        sb.append("> Extraites par analyse AST (JavaParser / TreeSitterAnalyzer)\n\n");

        ServiceEcosystem eco = service.ecosystem();
        if (eco != null) {
            sb.append("## Entrantes (Controllers qui appellent ce service)\n\n");
            if (eco.callingControllers().isEmpty()) {
                sb.append("_Aucun controller appelant détecté._\n");
            } else {
                eco.callingControllers().forEach(c -> sb.append("- `").append(c).append("`\n"));
            }

            sb.append("\n## Sortantes\n\n");
            sb.append("### Repositories\n");
            if (eco.usedRepositories().isEmpty()) {
                sb.append("_Aucun repository injecté._\n");
            } else {
                eco.usedRepositories().forEach(r -> sb.append("- `").append(r).append("`\n"));
            }

            sb.append("\n### Services dépendants\n");
            if (eco.dependentServices().isEmpty()) {
                sb.append("_Aucun service dépendant._\n");
            } else {
                eco.dependentServices().forEach(s -> sb.append("- `").append(s).append("`\n"));
            }

            sb.append("\n### DTOs utilisés\n");
            if (eco.usedDtos().isEmpty()) {
                sb.append("_Aucun DTO détecté._\n");
            } else {
                eco.usedDtos().forEach(d -> sb.append("- `").append(d).append("`\n"));
            }
        }

        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "tree-sitter-ast"));
    }

    // =========================================================================
    // API PUBLIQUE
    // =========================================================================

    public static List<DocFragment> generatePublicApi(DetectedService service) {
        return generatePublicApi(service, null);
    }

    public static List<DocFragment> generatePublicApi(DetectedService service, AstFileSummary astSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# API Publique\n\n");
        sb.append("> Extraite par analyse AST (JavaParser / TreeSitterAnalyzer)\n\n");

        ServiceEcosystem eco = service.ecosystem();

        if (eco != null && !eco.publicApi().isEmpty()) {
            sb.append("| Méthode | Paramètres (types AST) | Retour | Async |\n");
            sb.append("|---------|------------------------|--------|-------|\n");

            for (PublicMethod pm : eco.publicApi()) {
                String params = pm.parameterTypes().isEmpty() ? "—"
                        : String.join(", ", pm.parameterTypes());
                sb.append("| `").append(pm.name()).append("` | `")
                        .append(params).append("` | `")
                        .append(pm.returnType()).append("` | ")
                        .append(pm.isAsync() ? "✅ Async" : "Non").append(" |\n");
            }

            return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "tree-sitter-ast"));

        } else if (astSummary != null && !astSummary.methods().isEmpty()) {
            // Fallback : méthodes depuis AstFileSummary (noms uniquement)
            sb.append("| Méthode | Paramètres | Retour | Async |\n");
            sb.append("|---------|------------|--------|-------|\n");
            astSummary.methods().forEach(m ->
                    sb.append("| `").append(m).append("` | — | — | — |\n"));

            return List.of(new DocFragment(sb.toString(), ProvenanceLevel.INFERRED, "tree-sitter-ast"));

        } else {
            sb.append("_Aucune méthode publique détectée par l'analyse AST._\n");
            return List.of(new DocFragment(sb.toString(), ProvenanceLevel.PLACEHOLDER, "tree-sitter-ast"));
        }
    }

    // =========================================================================
    // COMMITS
    // =========================================================================

    public static List<DocFragment> generateCommits(DetectedService service, List<GitHubCommit> commits) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Historique des Commits\n\n");
        sb.append("*").append(commits.size()).append(" commits couvrant l'écosystème.*\n\n");

        for (GitHubCommit commit : commits) {
            sb.append("- **").append(commit.commit().author().date()).append("** : ")
                    .append(commit.commit().message().replace("\n", " "))
                    .append(" (").append(commit.commit().author().name()).append(")\n");
        }

        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "github-api"));
    }

    // =========================================================================
    // CONTRIBUTEURS
    // =========================================================================

    public static List<DocFragment> generateContributors(DetectedService service, List<ContributorStats> contributors) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Contributeurs\n\n");
        sb.append("| Nom | Email | Commits | Premier Commit | Dernier Commit |\n");
        sb.append("|---|---|---|---|---|\n");

        for (ContributorStats c : contributors) {
            sb.append("| ").append(c.name())
                    .append(" | ").append(c.email())
                    .append(" | ").append(c.commitCount())
                    .append(" | ").append(c.firstCommit())
                    .append(" | ").append(c.lastCommit()).append(" |\n");
        }

        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "github-api"));
    }

    // =========================================================================
    // META
    // =========================================================================

    public static List<DocFragment> generateMeta(DetectedService service,
            RepositorySnapshot snapshot, long startTimeMs, int cacheMisses) {
        long duration = System.currentTimeMillis() - startTimeMs;
        StringBuilder sb = new StringBuilder();
        sb.append("# Meta\n\n");
        sb.append("- **Date** : ").append(java.time.Instant.now()).append("\n");
        sb.append("- **Branche** : `").append(snapshot.branch()).append("`\n");
        sb.append("- **SHA** : ")
                .append(snapshot.tree().isEmpty() ? "N/A" : snapshot.tree().get(0).sha())
                .append("\n");
        sb.append("- **Durée d'exécution** : ").append(duration).append(" ms\n");
        sb.append("- **Appels GitHub (misses)** : ").append(cacheMisses).append("\n");
        sb.append("- **Moteur d'analyse** : JavaParser (AST) + TreeSitterAnalyzer\n");

        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "system"));
    }

    // =========================================================================
    // README
    // =========================================================================

    public static List<DocFragment> generateReadme(DetectedService service,
            Map<DocSection, List<DocFragment>> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(service.className()).append("\n\n");
        sb.append("Résumé du service : gère la logique métier pour `")
                .append(service.className().replace("Service", "")).append("`.\n\n");

        // Qualité basée sur la provenance AST
        boolean hasInferred = false;
        boolean hasPlaceholder = false;
        for (List<DocFragment> fragments : sections.values()) {
            if (fragments != null && !fragments.isEmpty()) {
                ProvenanceLevel lvl = fragments.get(0).level();
                if (lvl == ProvenanceLevel.INFERRED)    hasInferred = true;
                if (lvl == ProvenanceLevel.PLACEHOLDER) hasPlaceholder = true;
            }
        }

        if (hasPlaceholder) {
            sb.append("🔴 **Limité** — certaines sections n'ont pas pu être extraites par l'AST.\n\n");
        } else if (hasInferred) {
            sb.append("🟡 **Partiel** — certaines sections sont inférées (AST incomplet).\n\n");
        } else {
            sb.append("🟢 **Complet** — toutes les sections sont extraites par analyse AST.\n\n");
        }

        sb.append("## Fichiers\n");
        sb.append("- [01-description.md](01-description.md)\n");
        sb.append("- [02-class-diagram.md](02-class-diagram.md)\n");
        sb.append("- [03-sequence-diagram.md](03-sequence-diagram.md)\n");
        sb.append("- [04-components.md](04-components.md)\n");
        sb.append("- [05-dependencies.md](05-dependencies.md)\n");
        sb.append("- [06-public-api.md](06-public-api.md)\n");
        sb.append("- [07-commits.md](07-commits.md)\n");
        sb.append("- [08-contributors.md](08-contributors.md)\n");
        sb.append("- [09-meta.md](09-meta.md)\n");

        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "tree-sitter-ast"));
    }
}
