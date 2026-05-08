package com.example.mcp_github.model;

import java.util.List;

public record ServiceEcosystem(
        List<String> implementsInterfaces,
        List<String> callingControllers,
        List<String> usedRepositories,
        List<String> manipulatedEntities,
        List<String> usedDtos,
        List<String> dependentServices,
        List<PublicMethod> publicApi
) {}
