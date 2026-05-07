package com.example.mcp_github.service.analyzer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;

@Component
public class MvcDetector extends BasePatternDetector {

    @Override
    public ArchitecturePattern getPattern() {
        return ArchitecturePattern.MVC;
    }

    @Override
    public PatternScore score(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "controllers") || hasFolder(paths, "controller")) {
            signals.add("Controllers/ folder");
            score += 30;
        }
        if (hasFolder(paths, "services") || hasFolder(paths, "service")) {
            signals.add("Services/ folder");
            score += 25;
        }
        if (hasFolder(paths, "models") || hasFolder(paths, "model")
                || hasFolder(paths, "entities") || hasFolder(paths, "entity")
                || hasFolder(paths, "domain")) {
            signals.add("Models/Entities/ folder");
            score += 20;
        }
        if (hasFolder(paths, "repositories") || hasFolder(paths, "repository")
                || hasFolder(paths, "dao")) {
            signals.add("Repositories/ folder");
            score += 15;
        }
        if (hasFolder(paths, "views") || hasFolder(paths, "view")
                || hasFolder(paths, "templates")) {
            signals.add("Views/Templates/ folder");
            score += 10;
        }

        return new PatternScore(getPattern(), Math.min(score, 100), signals);
    }
}
