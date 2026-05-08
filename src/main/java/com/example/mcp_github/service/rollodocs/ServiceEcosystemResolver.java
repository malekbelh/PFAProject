package com.example.mcp_github.service.rollodocs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.DetectedService;
import com.example.mcp_github.model.PublicMethod;
import com.example.mcp_github.model.ServiceEcosystem;
import com.example.mcp_github.service.GitHubFileTreeService;
import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;

@Service
public class ServiceEcosystemResolver {

    private final GitHubFileTreeService fileTreeService;

    public ServiceEcosystemResolver(GitHubFileTreeService fileTreeService) {
        this.fileTreeService = fileTreeService;
    }

    public ServiceEcosystem resolve(DetectedService service, RepositorySnapshot snapshot) {
        String content = fileTreeService.fetchFileContent(snapshot.owner(), snapshot.repo(), snapshot.branch(), service.path());
        if (content == null) {
            return new ServiceEcosystem(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        List<String> implementsInterfaces = extractInterfaces(content);
        List<String> usedRepositories = extractUsedClasses(content, "Repository");
        List<String> dependentServices = extractUsedClasses(content, "Service");
        List<String> usedDtos = extractUsedClasses(content, "DTO", "Dto", "Request", "Response");
        List<String> manipulatedEntities = extractEntities(content, snapshot.allPaths());
        List<PublicMethod> publicApi = extractPublicMethods(content);
        List<String> callingControllers = findCallingControllers(service.className(), snapshot);

        return new ServiceEcosystem(
                implementsInterfaces,
                callingControllers,
                usedRepositories,
                manipulatedEntities,
                usedDtos,
                dependentServices,
                publicApi
        );
    }

    private List<String> extractInterfaces(String content) {
        List<String> interfaces = new ArrayList<>();
        Pattern pattern = Pattern.compile("implements\\s+([A-Za-z0-9_,\\s]+)\\s*\\{");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String[] parts = matcher.group(1).split(",");
            for (String part : parts) {
                interfaces.add(part.trim());
            }
        }
        return interfaces;
    }

    private List<String> extractUsedClasses(String content, String... suffixes) {
        List<String> used = new ArrayList<>();
        // Simple extraction based on variable declarations or constructor injections
        Pattern pattern = Pattern.compile("([A-Z][A-Za-z0-9_]+)\\s+[a-z][A-Za-z0-9_]*\\s*[;=,)]");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String className = matcher.group(1);
            for (String suffix : suffixes) {
                if (className.endsWith(suffix) && !used.contains(className)) {
                    used.add(className);
                }
            }
        }
        return used;
    }

    private List<String> extractEntities(String content, List<String> allPaths) {
        List<String> entities = new ArrayList<>();
        // Entities are harder to guess by suffix if they don't have one.
        // We'll look for capitalized words that match filenames in entity/model folders.
        List<String> entityNames = allPaths.stream()
                .filter(p -> p.contains("/entity/") || p.contains("/entities/") || p.contains("/model/") || p.contains("/models/"))
                .map(p -> p.substring(p.lastIndexOf('/') + 1).replace(".java", ""))
                .collect(Collectors.toList());

        for (String entityName : entityNames) {
            if (content.contains(entityName) && !entities.contains(entityName)) {
                entities.add(entityName);
            }
        }
        return entities;
    }

    private List<PublicMethod> extractPublicMethods(String content) {
        List<PublicMethod> methods = new ArrayList<>();
        // Very basic regex for public method signatures
        Pattern pattern = Pattern.compile("public\\s+(?!class|interface|enum|record)([A-Za-z0-9_<>\\[\\]]+)\\s+([a-z][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String returnType = matcher.group(1).trim();
            String name = matcher.group(2).trim();
            String paramsStr = matcher.group(3).trim();

            List<String> params = new ArrayList<>();
            if (!paramsStr.isEmpty()) {
                for (String p : paramsStr.split(",")) {
                    params.add(p.trim());
                }
            }

            boolean isAsync = content.contains("@Async") && content.indexOf("@Async") < matcher.start(); // Very naive
            methods.add(new PublicMethod(name, params, returnType, isAsync, "public"));
        }
        return methods;
    }

    private List<String> findCallingControllers(String serviceName, RepositorySnapshot snapshot) {
        List<String> controllers = new ArrayList<>();
        List<String> controllerPaths = snapshot.allPaths().stream()
                .filter(p -> p.endsWith("Controller.java"))
                .collect(Collectors.toList());

        for (String path : controllerPaths) {
            String content = fileTreeService.fetchFileContent(snapshot.owner(), snapshot.repo(), snapshot.branch(), path);
            if (content != null && content.contains(serviceName)) {
                String className = path.substring(path.lastIndexOf('/') + 1).replace(".java", "");
                controllers.add(className);
            }
        }
        return controllers;
    }
}
