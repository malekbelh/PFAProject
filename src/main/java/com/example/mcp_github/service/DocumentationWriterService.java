package com.example.mcp_github.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentationWriterService {

    private static final Logger log = LoggerFactory.getLogger(DocumentationWriterService.class);

    public sealed interface WriteResult permits WriteResult.Success, WriteResult.Skipped, WriteResult.Failed {

        record Success(Path path, int bytes) implements WriteResult {

        }

        record Skipped(String reason) implements WriteResult {

        }

        record Failed(String reason) implements WriteResult {

        }
    }

    /**
     * Écrit la documentation dans rollo.md ou rollo_detailled.md.
     *
     * La synthèse chat (chatSummary) est injectée comme première section du
     * fichier. Le corps technique (docBody) suit immédiatement. Résultat : un
     * seul document complet, sans duplication entre chat et fichier.
     *
     * @param projectPath chemin absolu de la racine du projet
     * @param chatSummary synthèse courte destinée au chat — injectée en tête du
     * fichier
     * @param docBody corps technique complet de la documentation
     * @param detailed true → rollo_detailled.md | false → rollo.md
     * @param owner owner GitHub (pour les logs)
     * @param repo repo GitHub (pour les logs)
     * @param branch branche (pour les logs)
     */
    public WriteResult write(String projectPath,
            String chatSummary,
            String docBody,
            boolean detailed,
            String owner, String repo, String branch) {

        if (projectPath == null || projectPath.isBlank() || projectPath.equals("manual")) {
            return new WriteResult.Skipped(
                    "Chemin inconnu — lancez `initializeProject` ou `detectProjectFromPath`.");
        }

        Path root = Paths.get(projectPath);
        if (!Files.isDirectory(root)) {
            log.warn("Chemin inaccessible : {}", projectPath);
            return new WriteResult.Skipped(
                    "Chemin inaccessible : `%s` — lancez `initializeProject`.".formatted(projectPath));
        }

        String fileName = detailed ? "rollo_detailled.md" : "rollo.md";
        Path outPath = root.resolve(fileName);
        String fullContent = assembleFull(chatSummary, docBody);

        try {
            Files.writeString(outPath, fullContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Documentation sauvegardée : {} ({} octets) → {}",
                    fileName, fullContent.length(), outPath.toAbsolutePath());

            return new WriteResult.Success(outPath, fullContent.length());

        } catch (Exception e) {
            log.error("Échec d'écriture de {} dans {} : {}", fileName, projectPath, e.getMessage());
            return new WriteResult.Failed("Erreur d'écriture : " + e.getMessage());
        }
    }

    public WriteResult writeRefined(String projectPath,
            String reviewHeader,
            String refinedBody,
            String owner, String repo, String branch) {

        if (projectPath == null || projectPath.isBlank() || projectPath.equals("manual")) {
            return new WriteResult.Skipped(
                    "Chemin inconnu — lancez `initializeProject` ou `detectProjectFromPath`.");
        }

        Path root = Paths.get(projectPath);
        if (!Files.isDirectory(root)) {
            log.warn("Chemin inaccessible : {}", projectPath);
            return new WriteResult.Skipped(
                    "Chemin inaccessible : `%s` — lancez `initializeProject`.".formatted(projectPath));
        }

        Path outPath = root.resolve("rollo_refined.md");
        String fullContent = assembleRefined(reviewHeader, refinedBody);

        try {
            Files.writeString(outPath, fullContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Refined doc sauvegardée : rollo_refined.md ({} octets) → {}",
                    fullContent.length(), outPath.toAbsolutePath());

            return new WriteResult.Success(outPath, fullContent.length());

        } catch (Exception e) {
            log.error("Échec d'écriture de rollo_refined.md dans {} : {}", projectPath, e.getMessage());
            return new WriteResult.Failed("Erreur d'écriture : " + e.getMessage());
        }
    }

    /**
     * Génère la documentation spécifique par service dans rollo_docs/<service>/
     */
    public WriteResult writeServiceDoc(String projectPath, String serviceName,
            String description, String diagrams, String technicalData, String logic, String commits, String changes) {
        if (projectPath == null || projectPath.isBlank() || projectPath.equals("manual")) {
            return new WriteResult.Skipped("Chemin inconnu.");
        }

        Path root = Paths.get(projectPath);
        Path serviceDir = root.resolve("rollo_docs").resolve(serviceName);

        try {
            Files.createDirectories(serviceDir);
            
            if (description != null) {
                Files.writeString(serviceDir.resolve("description.md"), description, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            if (diagrams != null) {
                Files.writeString(serviceDir.resolve("diagrams.md"), diagrams, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            if (technicalData != null) {
                Files.writeString(serviceDir.resolve("technical_data.md"), technicalData, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            if (logic != null) {
                Files.writeString(serviceDir.resolve("logic.md"), logic, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            if (commits != null) {
                Files.writeString(serviceDir.resolve("commits.md"), commits, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            if (changes != null) {
                Files.writeString(serviceDir.resolve("changes.md"), changes, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            log.info("Documentation service sauvegardée : {}", serviceDir.toAbsolutePath());
            return new WriteResult.Success(serviceDir, 0);

        } catch (Exception e) {
            log.error("Échec d'écriture du service {} : {}", serviceName, e.getMessage());
            return new WriteResult.Failed("Erreur d'écriture : " + e.getMessage());
        }
    }

    /**
     * Assembles the review header + refined body into a single Markdown
     * document.
     */
    private String assembleRefined(String reviewHeader, String refinedBody) {
        StringBuilder sb = new StringBuilder();

        if (reviewHeader != null && !reviewHeader.isBlank()) {
            sb.append(reviewHeader.strip());
            sb.append("\n\n");
        }

        if (refinedBody != null && !refinedBody.isBlank()) {
            sb.append(refinedBody.strip());
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Assemble synthèse chat + corps technique en un seul document Markdown. La
     * synthèse apparaît en premier sous le titre "## Synthèse".
     */
    private String assembleFull(String chatSummary, String docBody) {
        StringBuilder sb = new StringBuilder();

        if (chatSummary != null && !chatSummary.isBlank()) {
            sb.append("## Synthèse\n\n");
            sb.append(chatSummary.strip());
            sb.append("\n\n---\n\n");
        }

        if (docBody != null && !docBody.isBlank()) {
            sb.append(docBody.strip());
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Confirmation courte retournée dans le chat — une seule ligne.
     */
    public String formatSuccessMessage(WriteResult.Success result,
            String owner, String repo, String branch) {
        String fileName = result.path().getFileName().toString();
        return "✅ `%s` généré (%d octets) → `%s`"
                .formatted(fileName, result.bytes(), result.path().toAbsolutePath());
    }

    /**
     * Avertissement court en cas d'échec ou d'écriture ignorée.
     */
    public String formatWarningMessage(String reason) {
        return "⚠️ Fichier non sauvegardé — %s".formatted(reason);
    }
}
