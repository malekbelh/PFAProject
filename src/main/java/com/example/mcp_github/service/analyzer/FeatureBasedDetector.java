package com.example.mcp_github.service.analyzer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;

@Component
public class FeatureBasedDetector extends BasePatternDetector {

    @Override
    public ArchitecturePattern getPattern() {
        return ArchitecturePattern.FEATURE_BASED;
    }

    @Override
    public PatternScore score(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFolder(paths, "feature") || hasFolder(paths, "features")) {
            signals.add("Features/ folder");
            score += 50;
        }
        if (hasFolder(paths, "module") || hasFolder(paths, "modules")) {
            signals.add("Modules/ folder");
            score += 40;
        }

        long featureLikeFolders = paths.stream()
                .map(p -> p.contains("/") ? p.substring(0, p.indexOf('/')) : "")
                .distinct()
                .filter(f -> !f.isBlank())
                .filter(f -> {
                    String fLower = f.toLowerCase();
                    boolean hasCtrl = paths.stream().anyMatch(
                            p -> p.toLowerCase().startsWith(fLower + "/") && p.toLowerCase().contains("controller"));
                    boolean hasSvc = paths.stream().anyMatch(
                            p -> p.toLowerCase().startsWith(fLower + "/") && p.toLowerCase().contains("service"));
                    return hasCtrl && hasSvc;
                })
                .count();

        if (featureLikeFolders >= 2) {
            signals.add(featureLikeFolders + " self-contained feature modules detected");
            score += 40;
        }

        return new PatternScore(getPattern(), Math.min(score, 100), signals);
    }
}
