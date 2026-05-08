package com.example.mcp_github.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.mcp_github.model.ComponentGroup;
import com.example.mcp_github.model.ComponentRole;
import com.example.mcp_github.model.ProjectFingerprint;
import com.example.mcp_github.service.ProjectStructureAnalyzer.AnalysisResult;

@Service
public class DocumentationContextBuilder {

    public ProjectFingerprint buildFingerprint(AnalysisResult analysis, List<ComponentGroup> groups) {
        
        Map<String, Integer> countsByRole = new HashMap<>();
        List<String> allWarnings = new ArrayList<>();
        
        for (ComponentRole role : analysis.inferredRoles()) {
            countsByRole.merge(role.role().name(), 1, Integer::sum);
            allWarnings.addAll(role.warnings());
        }
        
        return new ProjectFingerprint(
            analysis.stack(),
            analysis.pattern(),
            analysis.confidence(),
            analysis.matchedSignals(),
            countsByRole,
            groups,
            allWarnings
        );
    }
    
    public String buildPromptContext(ProjectFingerprint fingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ARCHITECTURAL FINGERPRINT ===\n\n");
        
        sb.append("Stack:\n");
        sb.append("- Languages: ").append(fingerprint.stack().languages()).append("\n");
        sb.append("- Frameworks: ").append(fingerprint.stack().frameworks()).append("\n");
        
        sb.append("\nArchitecture:\n");
        sb.append("- Primary Pattern: ").append(fingerprint.primaryPattern()).append(" (Confidence: ").append(fingerprint.patternConfidence()).append("%)\n");
        sb.append("- Key Signals: ").append(fingerprint.keySignals()).append("\n");
        
        sb.append("\nComponent Distribution:\n");
        fingerprint.componentCountsByRole().forEach((role, count) -> {
            sb.append("- ").append(role).append(": ").append(count).append("\n");
        });
        
        if (!fingerprint.architecturalWarnings().isEmpty()) {
            sb.append("\nDetected Warnings/Violations:\n");
            for (String warning : fingerprint.architecturalWarnings()) {
                sb.append("- ").append(warning).append("\n");
            }
        }
        
        sb.append("\nModules/Groups:\n");
        for (ComponentGroup group : fingerprint.groups()) {
            sb.append("- ").append(group.name()).append(" (").append(group.groupingCriterion()).append("): ").append(group.members().size()).append(" components\n");
        }
        
        return sb.toString();
    }
}
