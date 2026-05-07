package com.example.mcp_github.service.analyzer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;

@Component
public class CleanArchitectureDetector extends BasePatternDetector {

    @Override
    public ArchitecturePattern getPattern() {
        return ArchitecturePattern.CLEAN_ARCHITECTURE;
    }

    @Override
    public PatternScore score(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "domain")) {
            signals.add("Domain/ layer");
            score += 30;
        }
        if (hasFolder(paths, "application") || hasFolder(paths, "usecase")
                || hasFolder(paths, "usecases") || hasFolder(paths, "use-cases")) {
            signals.add("Application/UseCase/ layer");
            score += 30;
        }
        if (hasFolder(paths, "infrastructure") || hasFolder(paths, "infra")) {
            signals.add("Infrastructure/ layer");
            score += 25;
        }
        if (hasFolder(paths, "presentation") || hasFolder(paths, "adapter")
                || hasFolder(paths, "adapters")) {
            signals.add("Presentation/Adapter/ layer");
            score += 15;
        }

        return new PatternScore(getPattern(), Math.min(score, 100), signals);
    }
}
