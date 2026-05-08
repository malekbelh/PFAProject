package com.example.mcp_github.model;

import java.util.List;
import java.util.Map;

import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.Stack;

/**
 * A highly structured representation of the project's architectural state.
 */
public record ProjectFingerprint(
    Stack stack,
    ArchitecturePattern primaryPattern,
    int patternConfidence,
    List<String> keySignals,
    Map<String, Integer> componentCountsByRole,
    List<ComponentGroup> groups,
    List<String> architecturalWarnings
) {}
