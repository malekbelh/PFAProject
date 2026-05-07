package com.example.mcp_github.service.analyzer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;

@Component
public class LayeredDetector extends BasePatternDetector {

    @Override
    public ArchitecturePattern getPattern() {
        return ArchitecturePattern.LAYERED;
    }

    @Override
    public PatternScore score(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int layers = 0;

        if (hasFolder(paths, "presentation") || hasFolder(paths, "web")
                || hasFolder(paths, "api")) {
            signals.add("Presentation/Web/ layer");
            layers++;
        }
        if (hasFolder(paths, "business") || hasFolder(paths, "logic")
                || hasFolder(paths, "service") || hasFolder(paths, "services")) {
            signals.add("Business/Service/ layer");
            layers++;
        }
        if (hasFolder(paths, "data") || hasFolder(paths, "persistence")
                || hasFolder(paths, "repository") || hasFolder(paths, "repositories")) {
            signals.add("Data/Persistence/ layer");
            layers++;
        }

        int score = layers * 30;
        return new PatternScore(getPattern(), Math.min(score, 100), signals);
    }
}
