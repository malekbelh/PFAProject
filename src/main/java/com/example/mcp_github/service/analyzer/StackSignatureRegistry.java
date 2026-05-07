package com.example.mcp_github.service.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StackSignatureRegistry {

    public record LanguageSignature(String extension, String name) {

    }

    public record BuildToolSignature(String fileName, String name) {

    }

    private final List<LanguageSignature> languages = List.of(
            new LanguageSignature(".java", "Java"),
            new LanguageSignature(".kt", "Kotlin"),
            new LanguageSignature(".ts", "TypeScript"),
            new LanguageSignature(".tsx", "TypeScript"),
            new LanguageSignature(".js", "JavaScript"),
            new LanguageSignature(".jsx", "JavaScript"),
            new LanguageSignature(".py", "Python"),
            new LanguageSignature(".go", "Go"),
            new LanguageSignature(".rs", "Rust"),
            new LanguageSignature(".cs", "C#"),
            new LanguageSignature(".rb", "Ruby"),
            new LanguageSignature(".cpp", "C++")
    );

    private final List<BuildToolSignature> buildTools = List.of(
            new BuildToolSignature("pom.xml", "Maven"),
            new BuildToolSignature("build.gradle", "Gradle"),
            new BuildToolSignature("package.json", "npm/yarn"),
            new BuildToolSignature("requirements.txt", "pip"),
            new BuildToolSignature("go.mod", "Go modules"),
            new BuildToolSignature("cargo.toml", "Cargo")
    );

    public List<String> detectLanguages(List<String> paths) {
        return languages.stream()
                .filter(sig -> paths.stream().anyMatch(p -> p.toLowerCase().endsWith(sig.extension())))
                .map(LanguageSignature::name)
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> detectBuildTools(List<String> paths) {
        return buildTools.stream()
                .filter(sig -> paths.stream().anyMatch(p -> {
            String lp = p.toLowerCase();
            return lp.equals(sig.fileName()) || lp.endsWith("/" + sig.fileName());
        }))
                .map(BuildToolSignature::name)
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> detectFrameworks(Map<String, String> files) {
        List<String> frameworks = new ArrayList<>();

        String pom = files.getOrDefault("pom.xml", "").toLowerCase();
        if (pom.contains("spring-boot")) {
            frameworks.add("Spring Boot");
        }
        if (pom.contains("spring-security")) {
            frameworks.add("Spring Security");
        }
        if (pom.contains("quarkus")) {
            frameworks.add("Quarkus");
        }

        String pkgJson = files.getOrDefault("package.json", "").toLowerCase();
        if (pkgJson.contains("\"react\"")) {
            frameworks.add("React");
        }
        if (pkgJson.contains("\"next\"")) {
            frameworks.add("Next.js");
        }
        if (pkgJson.contains("\"express\"")) {
            frameworks.add("Express");
        }

        return frameworks.stream().distinct().sorted().toList();
    }
}
