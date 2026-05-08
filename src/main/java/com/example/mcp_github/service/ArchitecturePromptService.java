package com.example.mcp_github.service;

import com.example.mcp_github.model.ReferenceArchitectures;
import com.example.mcp_github.model.ProjectFingerprint;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import org.springframework.stereotype.Service;


import java.util.List;

/**
 * Service to generate structured prompts for architectural analysis.
 * Supports Zero, One, and Few-shot prompting strategies.
 */
@Service
public class ArchitecturePromptService {

    private final DocumentationContextBuilder contextBuilder;

    public ArchitecturePromptService(DocumentationContextBuilder contextBuilder) {
        this.contextBuilder = contextBuilder;
    }

    /**
     * Generates an enhanced prompt based on the architectural fingerprint.
     */
    public String generateEnhancedPrompt(ProjectFingerprint fingerprint, String userPrompt) {
        StringBuilder sb = new StringBuilder();
        
        // 1. Core Instruction (Zero-shot foundation reflecting Hybrid Architecture)
        sb.append("Act as a Principal Software Architect. I have just generated a deterministic architectural analysis of this project using static code analysis tools. Read this context deeply so you can understand the project structure, answer the developer's questions accurately, and prepare for the upcoming peer-review phase.\n\n");
        
        if (userPrompt != null && !userPrompt.isBlank()) {
            sb.append("Focused Objective: ").append(userPrompt).append("\n\n");
        }

        // 2. Contextual Shots (One-shot / Few-shot)
        appendContextualShots(sb, fingerprint);

        // 3. Current Project Context
        if (fingerprint != null) {
            sb.append(contextBuilder.buildPromptContext(fingerprint));
        }

        sb.append("\nPlease perform a deep analysis following the pattern above.");
        
        return sb.toString();
    }

    private void appendContextualShots(StringBuilder sb, ProjectFingerprint fingerprint) {
        sb.append("Reference Architectural Examples:\n");
        
        if (fingerprint == null) return;

        // One-shot based on Stack
        boolean isJava = fingerprint.stack().languages().contains("Java");
        boolean isNode = fingerprint.stack().languages().contains("JavaScript") || fingerprint.stack().languages().contains("TypeScript");

        if (isJava) {
            sb.append(ReferenceArchitectures.SPRING_BOOT_MVC_SHOT).append("\n");
        } else if (isNode) {
            sb.append(ReferenceArchitectures.NODE_EXPRESS_SHOT).append("\n");
        }

        // Few-shot based on Pattern Confidence
        if (fingerprint.primaryPattern() == ArchitecturePattern.CLEAN_ARCHITECTURE || fingerprint.patternConfidence() < 50) {
            sb.append(ReferenceArchitectures.PATTERN_SHOTS.get("CLEAN")).append("\n");
        }
        
        if (fingerprint.primaryPattern() == ArchitecturePattern.HEXAGONAL) {
            sb.append(ReferenceArchitectures.PATTERN_SHOTS.get("HEXAGONAL")).append("\n");
        }

        sb.append("---\n\n");
    }
}
