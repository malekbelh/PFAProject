package com.example.mcp_github.model;

import java.util.List;

public record PublicMethod(
        String name,
        List<String> parameterTypes,
        String returnType,
        boolean isAsync,
        String visibility
) {}
