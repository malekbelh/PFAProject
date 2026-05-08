package com.example.mcp_github.model;

public record DetectedService(
        String className,
        String path,
        String packageName,
        DetectionReason reason,
        ServiceEcosystem ecosystem
) {}
