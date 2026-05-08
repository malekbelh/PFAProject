package com.example.mcp_github.service.rollodocs.generators;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.DetectedService;
import com.example.mcp_github.model.DocFragment;
import com.example.mcp_github.model.ProvenanceLevel;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;

@Service
public class RolloDocsIndexGenerator {

    public List<DocFragment> generate(List<DetectedService> services, RepositorySnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Index RolloDocs des Services\n\n");
        sb.append("Voici l'index de tous les services détectés.\n\n");
        
        sb.append("| Service | Raison Détection | Package | Écosystème | Lien |\n");
        sb.append("|---------|------------------|---------|------------|------|\n");
        
        for (DetectedService service : services) {
            int ecoSize = 0;
            if (service.ecosystem() != null) {
                ecoSize = service.ecosystem().implementsInterfaces().size() +
                          service.ecosystem().callingControllers().size() +
                          service.ecosystem().usedRepositories().size() +
                          service.ecosystem().usedDtos().size() +
                          service.ecosystem().manipulatedEntities().size() +
                          service.ecosystem().dependentServices().size();
            }
            
            sb.append("| `").append(service.className()).append("` ");
            sb.append("| `").append(service.reason()).append("` ");
            sb.append("| `").append(service.packageName()).append("` ");
            sb.append("| ").append(ecoSize).append(" classes liées ");
            sb.append("| [Détails](").append(service.className()).append("/README.md) |\n");
        }
        
        return List.of(new DocFragment(sb.toString(), ProvenanceLevel.OBSERVED, "static-analysis"));
    }
}
