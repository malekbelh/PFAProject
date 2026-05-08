package com.example.mcp_github.model;

/**
 * Represents a fragment of documentation with its content and provenance.
 */
public record DocFragment(
    String content,
    ProvenanceLevel level,
    String source
) {}
