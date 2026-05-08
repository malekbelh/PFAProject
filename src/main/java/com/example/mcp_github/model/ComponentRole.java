package com.example.mcp_github.model;

import java.util.List;

/**
 * Represents the role of a component within the architecture.
 */
public record ComponentRole(
    String path,                  // chemin du fichier
    String className,             // nom de classe extrait
    ArchitecturalRole role,       // enum forte
    String layer,                 // "presentation" | "application" | "domain" | "infrastructure"
    String service,               // bounded context déduit (ex. "github-service")
    double confidence,            // 0..1 — degré de certitude de la classification
    List<String> evidence,        // signaux ayant mené à la décision (ex: "endsWith Controller", "in /api/")
    List<String> warnings         // incohérences détectées (ex: "Controller dans /service/")
) {}
