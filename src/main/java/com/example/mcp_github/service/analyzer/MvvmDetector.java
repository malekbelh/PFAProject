package com.example.mcp_github.service.analyzer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;

@Component
public class MvvmDetector extends BasePatternDetector {

    @Override
    public ArchitecturePattern getPattern() {
        return ArchitecturePattern.MVVM;
    }

    @Override
    public PatternScore score(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "viewmodel") || hasFolder(paths, "viewmodels")) {
            signals.add("ViewModels/ folder");
            score += 50;
        }
        if (hasFolder(paths, "view") || hasFolder(paths, "views")) {
            signals.add("Views/ folder");
            score += 25;
        }
        if (hasFolder(paths, "model") || hasFolder(paths, "models")) {
            signals.add("Models/ folder");
            score += 25;
        }

        return new PatternScore(getPattern(), Math.min(score, 100), signals);
    }
}
