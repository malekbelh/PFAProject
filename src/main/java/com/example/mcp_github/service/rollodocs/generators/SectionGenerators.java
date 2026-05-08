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

public class SectionGenerators {

    public static List<DocFragment> generateDescription(DetectedService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Description de ").append(service.className()).append("\n\n");
        sb.append("- **Package** : `").append(service.packageName()).append("`\n");
        sb.append("- **Raison de détection** : `").append(service.reason()).append("`\n");
        
        ServiceEcosystem eco = service.ecosystem();
        if (eco != null) {
            sb.append("- **Interfaces implémentées** : ").append(String.join(", ", eco.implementsInterfaces())).append("\n");
            sb.append("- **Nombre de méthodes publiques** : ").append(eco.publicApi().size()).append("\n");
            sb.append("- **Dépendances** : ").append(eco.dependentServices().size() + eco.usedRepositories().size()).append("\n");
        }
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "static-analysis"));
    }

    public static List<DocFragment> generateClassDiagram(DetectedService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Diagramme de classes\n\n```mermaid\nclassDiagram\n");
        
        ServiceEcosystem eco = service.ecosystem();
        if (eco != null) {
            sb.append("class ").append(service.className()).append(" {\n");
            for (PublicMethod pm : eco.publicApi()) {
                sb.append("  +").append(pm.name()).append("(").append(String.join(", ", pm.parameterTypes())).append(") ").append(pm.returnType()).append("\n");
            }
            sb.append("}\n");
            
            for (String intf : eco.implementsInterfaces()) {
                sb.append(intf).append(" <|-- ").append(service.className()).append("\n");
            }
            for (String repo : eco.usedRepositories()) {
                sb.append(service.className()).append(" *-- ").append(repo).append("\n");
            }
            for (String srv : eco.dependentServices()) {
                sb.append(service.className()).append(" *-- ").append(srv).append("\n");
            }
            for (String entity : eco.manipulatedEntities()) {
                sb.append(service.className()).append(" --> ").append(entity).append("\n");
            }
            for (String dto : eco.usedDtos()) {
                sb.append(service.className()).append(" ..> ").append(dto).append("\n");
            }
        } else {
            sb.append("class ").append(service.className()).append("\n");
        }
        sb.append("```\n");
        return List.of(new DocFragment(sb.toString(), eco != null && !eco.publicApi().isEmpty() ? ProvenanceLevel.OBSERVED : ProvenanceLevel.INFERRED, "static-analysis"));
    }

    public static List<DocFragment> generateSequenceDiagram(DetectedService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Diagrammes de séquence\n\n");
        
        ServiceEcosystem eco = service.ecosystem();
        if (eco == null || eco.publicApi().isEmpty()) {
            sb.append("> [!NOTE]\n> Diagrammes non disponibles (aucune méthode publique détectée ou parsable).\n");
            return List.of(new DocFragment(sb.toString(), ProvenanceLevel.PLACEHOLDER, "static-analysis"));
        }
        
        for (PublicMethod pm : eco.publicApi()) {
            sb.append("## `").append(pm.name()).append("`\n\n");
            sb.append("```mermaid\nsequenceDiagram\n");
            sb.append("participant Caller\n");
            sb.append("participant ").append(service.className()).append("\n");
            sb.append("Caller->>").append(service.className()).append(": ").append(pm.name()).append("()\n");
            if (!eco.usedRepositories().isEmpty()) {
                sb.append(service.className()).append("->>").append(eco.usedRepositories().get(0)).append(": call()\n");
                sb.append(eco.usedRepositories().get(0)).append("-->>").append(service.className()).append(": data\n");
            }
            sb.append(service.className()).append("-->>Caller: ").append(pm.returnType()).append("\n");
            sb.append("```\n\n");
        }
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.INFERRED, "static-analysis"));
    }

    public static List<DocFragment> generateComponentsList(DetectedService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Composants de l'écosystème\n\n");
        sb.append("| Nom | Type |\n");
        sb.append("|---|---|\n");
        
        sb.append("| `").append(service.className()).append("` | Service |\n");
        
        ServiceEcosystem eco = service.ecosystem();
        if (eco != null) {
            for (String item : eco.implementsInterfaces()) sb.append("| `").append(item).append("` | Interface |\n");
            for (String item : eco.callingControllers()) sb.append("| `").append(item).append("` | Controller |\n");
            for (String item : eco.usedRepositories()) sb.append("| `").append(item).append("` | Repository |\n");
            for (String item : eco.manipulatedEntities()) sb.append("| `").append(item).append("` | Entity |\n");
            for (String item : eco.usedDtos()) sb.append("| `").append(item).append("` | DTO |\n");
            for (String item : eco.dependentServices()) sb.append("| `").append(item).append("` | Service Dépendant |\n");
        }
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "static-analysis"));
    }

    public static List<DocFragment> generateDependencies(DetectedService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Dépendances\n\n");
        
        ServiceEcosystem eco = service.ecosystem();
        if (eco != null) {
            sb.append("## Entrantes\n");
            for (String item : eco.callingControllers()) sb.append("- `").append(item).append("`\n");
            
            sb.append("\n## Sortantes\n");
            for (String item : eco.usedRepositories()) sb.append("- `").append(item).append("`\n");
            for (String item : eco.dependentServices()) sb.append("- `").append(item).append("`\n");
        }
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "static-analysis"));
    }

    public static List<DocFragment> generatePublicApi(DetectedService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("# API Publique\n\n");
        sb.append("| Méthode | Paramètres | Retour | Async |\n");
        sb.append("|---------|------------|--------|-------|\n");
        
        ServiceEcosystem eco = service.ecosystem();
        if (eco != null && !eco.publicApi().isEmpty()) {
            for (PublicMethod pm : eco.publicApi()) {
                sb.append("| `").append(pm.name()).append("` | `").append(String.join(", ", pm.parameterTypes())).append("` | `").append(pm.returnType()).append("` | ").append(pm.isAsync() ? "Oui" : "Non").append(" |\n");
            }
            return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "static-analysis"));
        } else {
            sb.append("| - | - | - | - |\n");
            return List.of(new DocFragment(sb.toString(), ProvenanceLevel.PLACEHOLDER, "static-analysis"));
        }
    }

    public static List<DocFragment> generateCommits(DetectedService service, List<GitHubCommit> commits) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Historique des Commits\n\n");
        sb.append("*").append(commits.size()).append(" commits couvrant l'écosystème.*\n\n");
        
        for (GitHubCommit commit : commits) {
            sb.append("- **").append(commit.commit().author().date()).append("** : ").append(commit.commit().message().replace("\n", " ")).append(" (").append(commit.commit().author().name()).append(")\n");
        }
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "github-api"));
    }

    public static List<DocFragment> generateContributors(DetectedService service, List<ContributorStats> contributors) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Contributeurs\n\n");
        sb.append("| Nom | Email | Commits | Premier Commit | Dernier Commit |\n");
        sb.append("|---|---|---|---|---|\n");
        
        for (ContributorStats c : contributors) {
            sb.append("| ").append(c.name()).append(" | ").append(c.email()).append(" | ").append(c.commitCount()).append(" | ").append(c.firstCommit()).append(" | ").append(c.lastCommit()).append(" |\n");
        }
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "github-api"));
    }

    public static List<DocFragment> generateMeta(DetectedService service, RepositorySnapshot snapshot, long startTimeMs, int cacheMisses) {
        StringBuilder sb = new StringBuilder();
        long duration = System.currentTimeMillis() - startTimeMs;
        sb.append("# Meta\n\n");
        sb.append("- **Date** : ").append(java.time.Instant.now().toString()).append("\n");
        sb.append("- **Branche** : `").append(snapshot.branch()).append("`\n");
        sb.append("- **SHA** : ").append(snapshot.tree().isEmpty() ? "N/A" : snapshot.tree().get(0).sha()).append("\n");
        sb.append("- **Durée d'exécution** : ").append(duration).append(" ms\n");
        sb.append("- **Appels GitHub (misses)** : ").append(cacheMisses).append("\n");
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "system"));
    }

    public static List<DocFragment> generateReadme(DetectedService service, Map<DocSection, List<DocFragment>> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(service.className()).append("\n\n");
        sb.append("Résumé du service : gère la logique métier pour `").append(service.className().replace("Service", "")).append("`.\n\n");
        
        boolean hasInferred = false;
        boolean hasPlaceholder = false;
        for (List<DocFragment> fragments : sections.values()) {
            if (fragments != null && !fragments.isEmpty()) {
                if (fragments.get(0).level() == ProvenanceLevel.INFERRED) hasInferred = true;
                if (fragments.get(0).level() == ProvenanceLevel.PLACEHOLDER) hasPlaceholder = true;
            }
        }
        
        if (hasPlaceholder) {
            sb.append("🔴 Limité (≥1 section PLACEHOLDER)\n\n");
        } else if (hasInferred) {
            sb.append("🟡 Partiel (≥1 section INFERRED)\n\n");
        } else {
            sb.append("🟢 Complet (toutes sections OBSERVED)\n\n");
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
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "static-analysis"));
    }
}
