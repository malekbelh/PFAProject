package com.example.mcp_github.model;

import java.util.List;

/**
 * Represents a group of components, grouped by a specific strategy.
 */
public record ComponentGroup(
    String name,
    GroupingCriterion groupingCriterion,
    List<String> members,
    String commonPackage,
    double confidence
) {
    public enum GroupingCriterion {
        MICROSERVICE_MODULE,
        SHARED_PACKAGE,
        LAYER_FALLBACK
    }
}
