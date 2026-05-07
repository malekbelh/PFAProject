package com.example.mcp_github.service.analyzer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;

@Component
public class MicroservicesDetector extends BasePatternDetector {

    @Override
    public ArchitecturePattern getPattern() {
        return ArchitecturePattern.MICROSERVICES;
    }

    @Override
    public PatternScore score(List<String> paths) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        if (hasFile(paths, "docker-compose.yml") || hasFile(paths, "docker-compose.yaml")) {
            signals.add("docker-compose.yml");
            score += 25;
        }
        if (hasFile(paths, "dockerfile")) {
            signals.add("Dockerfile");
            score += 20;
        }
        if (hasFolder(paths, "gateway") || hasFolder(paths, "api-gateway")) {
            signals.add("Gateway/ service");
            score += 25;
        }
        if (hasFolder(paths, "discovery") || hasFolder(paths, "registry")) {
            signals.add("Service discovery");
            score += 15;
        }

        long buildFiles = paths.stream()
                .filter(p -> {
                    String f = fileName(p.toLowerCase());
                    return f.equals("pom.xml") || f.equals("package.json");
                })
                .count();
        if (buildFiles >= 3) {
            signals.add(buildFiles + " build files (multi-module)");
            score += 15;
        }

        return new PatternScore(getPattern(), Math.min(score, 100), signals);
    }
}
