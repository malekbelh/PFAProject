package com.example.mcp_github.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.ComponentGroup;
import com.example.mcp_github.model.ComponentRole;
import com.example.mcp_github.model.ComponentGroup.GroupingCriterion;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;

@Service
public class ComponentGrouper {

    public List<ComponentGroup> groupComponents(RepositorySnapshot snapshot, List<ComponentRole> roles, ArchitecturePattern pattern) {
        if (pattern == ArchitecturePattern.MICROSERVICES) {
            return groupAsMicroservices(snapshot, roles);
        }
        
        // Default to package-based grouping (Strategy 2)
        List<ComponentGroup> packageGroups = groupByPackage(roles);
        if (!packageGroups.isEmpty() && packageGroups.size() > 1) {
            return packageGroups;
        }
        
        // Fallback to layer-based grouping
        return groupByLayer(roles);
    }
    
    private List<ComponentGroup> groupAsMicroservices(RepositorySnapshot snapshot, List<ComponentRole> roles) {
        // Find poms or package.json
        List<String> modules = snapshot.allPaths().stream()
            .filter(p -> p.endsWith("pom.xml") || p.endsWith("package.json") || p.endsWith("build.gradle"))
            .filter(p -> p.contains("/"))
            .map(p -> p.substring(0, p.lastIndexOf('/')))
            .distinct()
            .toList();
            
        if (modules.isEmpty()) {
            return groupByPackage(roles); // Fallback
        }
        
        List<ComponentGroup> groups = new ArrayList<>();
        for (String module : modules) {
            List<String> members = roles.stream()
                .filter(r -> r.path().startsWith(module + "/"))
                .map(ComponentRole::path)
                .toList();
                
            if (!members.isEmpty()) {
                String moduleName = module;
                if (moduleName.contains("/")) {
                    moduleName = moduleName.substring(moduleName.lastIndexOf('/') + 1);
                }
                groups.add(new ComponentGroup(
                    moduleName,
                    GroupingCriterion.MICROSERVICE_MODULE,
                    members,
                    module,
                    0.9
                ));
            }
        }
        return groups;
    }
    
    private List<ComponentGroup> groupByPackage(List<ComponentRole> roles) {
        // Simplistic package grouping based on 'service' extraction from class names for now
        Map<String, List<String>> serviceMap = new HashMap<>();
        for (ComponentRole role : roles) {
            serviceMap.computeIfAbsent(role.service(), k -> new ArrayList<>()).add(role.path());
        }
        
        List<ComponentGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : serviceMap.entrySet()) {
            groups.add(new ComponentGroup(
                entry.getKey(),
                GroupingCriterion.SHARED_PACKAGE,
                entry.getValue(),
                "unknown",
                0.7
            ));
        }
        return groups;
    }
    
    private List<ComponentGroup> groupByLayer(List<ComponentRole> roles) {
        Map<String, List<String>> layerMap = new HashMap<>();
        for (ComponentRole role : roles) {
            layerMap.computeIfAbsent(role.layer(), k -> new ArrayList<>()).add(role.path());
        }
        
        List<ComponentGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : layerMap.entrySet()) {
            groups.add(new ComponentGroup(
                entry.getKey(),
                GroupingCriterion.LAYER_FALLBACK,
                entry.getValue(),
                "layer",
                0.8
            ));
        }
        return groups;
    }
}
