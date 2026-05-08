package com.example.mcp_github.model;

/**
 * Architectural roles that a component can play in a system.
 */
public enum ArchitecturalRole {
    // Génériques
    ENTRY_POINT_HTTP, ENTRY_POINT_CLI, ENTRY_POINT_MESSAGING,
    APPLICATION_SERVICE, DOMAIN_SERVICE,
    REPOSITORY, GATEWAY, EXTERNAL_ADAPTER,
    DOMAIN_ENTITY, VALUE_OBJECT, AGGREGATE_ROOT, DTO,
    CONFIGURATION, SECURITY, CROSS_CUTTING_UTILITY,
    EXCEPTION_HANDLER, TEST_FIXTURE,
    
    // Pattern-spécifiques
    USE_CASE,            // Clean Architecture
    PORT_INPUT, PORT_OUTPUT, ADAPTER_INPUT, ADAPTER_OUTPUT,  // Hexagonal
    MCP_TOOL, MCP_RESOURCE,                                  // spécifique au projet
    
    UNCLASSIFIED
}
