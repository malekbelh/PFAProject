package com.example.mcp_github.service;

import com.example.mcp_github.model.ReferenceArchitectures;
import com.example.mcp_github.service.ProjectStructureAnalyzer.AnalysisResult;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import org.springframework.stereotype.Service;


import java.util.List;

/**
 * Service to generate structured prompts for architectural analysis.
 * Supports Zero, One, and Few-shot prompting strategies.
 */
@Service
public class ArchitecturePromptService {

    /**
     * Generates an enhanced prompt based on the analysis results.
     * Uses a specific "shot" strategy depending on the detected stack.
     */
    public String generateEnhancedPrompt(AnalysisResult analysis, String userPrompt) {
        StringBuilder sb = new StringBuilder();
        
        // 1. Core Instruction (Zero-shot foundation reflecting Hybrid Architecture)
        sb.append("Act as a Principal Software Architect. I have just generated a deterministic architectural analysis of this project using static code analysis tools. Read this context deeply so you can understand the project structure, answer the developer's questions accurately, and prepare for the upcoming peer-review phase.\n\n");
        
        if (userPrompt != null && !userPrompt.isBlank()) {
            sb.append("Focused Objective: ").append(userPrompt).append("\n\n");
        }

        // 2. Contextual Shots (One-shot / Few-shot)
        appendContextualShots(sb, analysis);

        // 3. Current Project Context
        sb.append("Current Project Context:\n");
        sb.append("- Languages: ").append(String.join(", ", analysis.stack().languages())).append("\n");
        sb.append("- Frameworks: ").append(String.join(", ", analysis.stack().frameworks())).append("\n");
        sb.append("- Detected Pattern: ").append(analysis.pattern()).append(" (Confidence: ").append(analysis.confidence()).append("%)\n");
        
        // 4. Semantic Roles (LLM Context Enrichment)
        if (analysis.inferredRoles() != null && !analysis.inferredRoles().isEmpty()) {
            sb.append("- Semantic Roles:\n");
            analysis.inferredRoles().forEach(role -> 
                sb.append("  * ").append(role.path()).append(" -> ").append(role.role()).append("\n")
            );
        }
        sb.append("\n");

        sb.append("Please perform a deep analysis following the pattern above.");
        
        return sb.toString();
    }

    private void appendContextualShots(StringBuilder sb, AnalysisResult analysis) {
        sb.append("Reference Architectural Examples:\n");
        
        // One-shot based on Stack
        boolean isJava = analysis.stack().languages().contains("Java");
        boolean isNode = analysis.stack().languages().contains("JavaScript") || analysis.stack().languages().contains("TypeScript");

        if (isJava) {
            sb.append(ReferenceArchitectures.SPRING_BOOT_MVC_SHOT).append("\n");
        } else if (isNode) {
            sb.append(ReferenceArchitectures.NODE_EXPRESS_SHOT).append("\n");
        }

        // Few-shot based on Pattern Confidence
        if (analysis.pattern() == ArchitecturePattern.CLEAN_ARCHITECTURE || analysis.confidence() < 50) {
            sb.append(ReferenceArchitectures.PATTERN_SHOTS.get("CLEAN")).append("\n");
        }
        
        if (analysis.pattern() == ArchitecturePattern.HEXAGONAL) {
            sb.append(ReferenceArchitectures.PATTERN_SHOTS.get("HEXAGONAL")).append("\n");
        }

        sb.append("---\n\n");
    }
}
