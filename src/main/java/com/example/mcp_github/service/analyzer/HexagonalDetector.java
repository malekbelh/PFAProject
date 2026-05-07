package com.example.mcp_github.service.analyzer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;

@Component
public class HexagonalDetector extends BasePatternDetector {

    @Override
    public ArchitecturePattern getPattern() {
        return ArchitecturePattern.HEXAGONAL;
    }

    @Override
    public PatternScore score(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "port") || hasFolder(paths, "ports")) {
            signals.add("Ports/ folder");
            score += 35;
        }
        if (hasFolder(paths, "adapter") || hasFolder(paths, "adapters")) {
            signals.add("Adapters/ folder");
            score += 35;
        }
        if (hasFolder(paths, "domain")) {
            signals.add("Domain/ folder");
            score += 20;
        }
        if (hasFolder(paths, "application")) {
            signals.add("Application/ folder");
            score += 10;
        }

        return new PatternScore(getPattern(), Math.min(score, 100), signals);
    }
}
