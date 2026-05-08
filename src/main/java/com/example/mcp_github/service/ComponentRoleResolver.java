package com.example.mcp_github.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.ArchitecturalRole;
import com.example.mcp_github.model.ComponentRole;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;

@Service
public class ComponentRoleResolver {

    public List<ComponentRole> resolveRoles(List<String> paths, ArchitecturePattern pattern) {
        List<ComponentRole> roles = new ArrayList<>();
        
        for (String path : paths) {
            if (!isSource(path)) continue;
            
            String className = extractClassName(path);
            if (className == null) continue;
            
            List<String> evidence = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            ArchitecturalRole role = determineRole(path, className, pattern, evidence);
            String layer = determineLayer(role);
            String service = extractService(path, className);
            
            // Basic consistency check
            if (role == ArchitecturalRole.ENTRY_POINT_HTTP && path.toLowerCase().contains("/service/")) {
                warnings.add("Controller placed in service package");
            }
            if (role == ArchitecturalRole.REPOSITORY && path.toLowerCase().contains("/controller/")) {
                warnings.add("Repository placed in controller package");
            }
            
            roles.add(new ComponentRole(
                path,
                className,
                role,
                layer,
                service,
                0.8, // Default confidence for now
                evidence,
                warnings
            ));
        }
        
        return roles;
    }
    
    private ArchitecturalRole determineRole(String path, String className, ArchitecturePattern pattern, List<String> evidence) {
        String lowerPath = path.toLowerCase();
        
        // 1. Check path specifics for certain patterns
        if (pattern == ArchitecturePattern.HEXAGONAL) {
            if (lowerPath.contains("/ports/in/") || lowerPath.contains("/port/in/")) {
                evidence.add("Path contains /ports/in/");
                return ArchitecturalRole.PORT_INPUT;
            }
            if (lowerPath.contains("/ports/out/") || lowerPath.contains("/port/out/")) {
                evidence.add("Path contains /ports/out/");
                return ArchitecturalRole.PORT_OUTPUT;
            }
            if (lowerPath.contains("/adapters/in/") || lowerPath.contains("/adapter/in/")) {
                evidence.add("Path contains /adapters/in/");
                return ArchitecturalRole.ADAPTER_INPUT;
            }
            if (lowerPath.contains("/adapters/out/") || lowerPath.contains("/adapter/out/")) {
                evidence.add("Path contains /adapters/out/");
                return ArchitecturalRole.ADAPTER_OUTPUT;
            }
        }
        
        if (pattern == ArchitecturePattern.CLEAN_ARCHITECTURE) {
            if (lowerPath.contains("/usecase") || lowerPath.contains("/use_case") || className.endsWith("UseCase")) {
                evidence.add("Path or name indicates Use Case");
                return ArchitecturalRole.USE_CASE;
            }
        }
        
        // 2. Generic heuristics
        if (className.endsWith("Controller") || lowerPath.contains("/controller/")) {
            evidence.add("Name ends with Controller or path contains /controller/");
            return ArchitecturalRole.ENTRY_POINT_HTTP;
        }
        if (className.endsWith("Service") || lowerPath.contains("/service/")) {
            evidence.add("Name ends with Service or path contains /service/");
            return pattern == ArchitecturePattern.CLEAN_ARCHITECTURE || pattern == ArchitecturePattern.HEXAGONAL 
                   ? ArchitecturalRole.DOMAIN_SERVICE : ArchitecturalRole.APPLICATION_SERVICE;
        }
        if (className.endsWith("Repository") || className.endsWith("Dao") || lowerPath.contains("/repository/")) {
            evidence.add("Name ends with Repository/Dao or path contains /repository/");
            return ArchitecturalRole.REPOSITORY;
        }
        if (className.endsWith("Entity") || className.endsWith("Model") || lowerPath.contains("/model/") || lowerPath.contains("/entity/")) {
            evidence.add("Name ends with Entity/Model or path contains /model/ or /entity/");
            return ArchitecturalRole.DOMAIN_ENTITY;
        }
        if (className.endsWith("Dto") || className.endsWith("Request") || className.endsWith("Response") || lowerPath.contains("/dto/")) {
            evidence.add("Name indicates DTO or path contains /dto/");
            return ArchitecturalRole.DTO;
        }
        if (className.endsWith("Config") || className.endsWith("Configuration") || lowerPath.contains("/config/")) {
            evidence.add("Name indicates Configuration or path contains /config/");
            return ArchitecturalRole.CONFIGURATION;
        }
        if (className.endsWith("Exception") || className.endsWith("Advice") || lowerPath.contains("/exception/")) {
            evidence.add("Name indicates Exception or path contains /exception/");
            return ArchitecturalRole.EXCEPTION_HANDLER;
        }
        if (lowerPath.contains("/security/") || className.contains("Security") || className.endsWith("Filter")) {
            evidence.add("Path or name indicates Security");
            return ArchitecturalRole.SECURITY;
        }
        if (className.endsWith("Tool") || lowerPath.contains("/tools/")) {
            evidence.add("Name ends with Tool or path contains /tools/");
            return ArchitecturalRole.MCP_TOOL;
        }
        if (lowerPath.contains("util") || lowerPath.contains("helper")) {
            evidence.add("Path contains util or helper");
            return ArchitecturalRole.CROSS_CUTTING_UTILITY;
        }
        if (lowerPath.contains("/test/") || className.endsWith("Test") || className.endsWith("Spec")) {
            evidence.add("Path contains test or name indicates Test");
            return ArchitecturalRole.TEST_FIXTURE;
        }
        
        evidence.add("No specific pattern matched");
        return ArchitecturalRole.UNCLASSIFIED;
    }
    
    private String determineLayer(ArchitecturalRole role) {
        return switch (role) {
            case ENTRY_POINT_HTTP, ENTRY_POINT_CLI, ENTRY_POINT_MESSAGING, ADAPTER_INPUT -> "presentation";
            case APPLICATION_SERVICE, USE_CASE -> "application";
            case DOMAIN_SERVICE, DOMAIN_ENTITY, VALUE_OBJECT, AGGREGATE_ROOT, PORT_INPUT, PORT_OUTPUT -> "domain";
            case REPOSITORY, GATEWAY, EXTERNAL_ADAPTER, ADAPTER_OUTPUT -> "infrastructure";
            case DTO, CONFIGURATION, SECURITY, CROSS_CUTTING_UTILITY, EXCEPTION_HANDLER, MCP_TOOL, MCP_RESOURCE -> "cross-cutting";
            case TEST_FIXTURE -> "test";
            default -> "unknown";
        };
    }
    
    private String extractService(String path, String className) {
        String[] suffixes = {"Controller", "Service", "Repository", "Entity", "Model", "DTO", "Request", "Response", "Mapper", "Impl", "Tools", "Tool", "UseCase"};
        String name = className;
        for (String s : suffixes) {
            if (name.endsWith(s)) {
                name = name.substring(0, name.length() - s.length());
                break;
            }
        }
        if (name.isEmpty() || name.length() < 2) {
            return "common-service";
        }
        name = name.replace("GitHub", "Github");
        String spinal = name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        return spinal + "-service";
    }
    
    private boolean isSource(String path) {
        return path.endsWith(".java") || path.endsWith(".kt") || path.endsWith(".ts") || 
               path.endsWith(".tsx") || path.endsWith(".js") || path.endsWith(".jsx") || 
               path.endsWith(".py") || path.endsWith(".go") || path.endsWith(".rs") || path.endsWith(".cs");
    }
    
    private String extractClassName(String path) {
        String f = path.substring(path.lastIndexOf('/') + 1);
        int dotIdx = f.lastIndexOf('.');
        return dotIdx > 0 ? f.substring(0, dotIdx) : f;
    }
}
