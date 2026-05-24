package com.example.mcp_github.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.mcp_github.model.ComponentGroup;
import com.example.mcp_github.model.DocFragment;
import com.example.mcp_github.model.DocSection;

@Service
public class DocumentationWriterService {

    private static final Logger log = LoggerFactory.getLogger(DocumentationWriterService.class);

    private final DocFragmentRenderer fragmentRenderer;

    public DocumentationWriterService(DocFragmentRenderer fragmentRenderer) {
        this.fragmentRenderer = fragmentRenderer;
    }

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
     * Génère la documentation spécifique par groupe dans rollo_docs/<type>/<groupe>/
     */
    public WriteResult writeServiceDoc(String projectPath, String serviceName, Map<DocSection, List<DocFragment>> sections) {
        if (projectPath == null || projectPath.isBlank() || projectPath.equals("manual")) {
            return new WriteResult.Skipped("Chemin inconnu.");
        }

        Path root = Paths.get(projectPath);
        Path groupDir = root.resolve("rollo_docs").resolve(serviceName);

        try {
            Files.createDirectories(groupDir);
            
            writeSection(groupDir, "README.md", sections.get(DocSection.README));
            writeSection(groupDir, "01-description.md", sections.get(DocSection.DESCRIPTION));
            writeSection(groupDir, "02-class-diagram.md", sections.get(DocSection.CLASS_DIAGRAM));
            writeSection(groupDir, "03-sequence-diagram.md", sections.get(DocSection.SEQUENCE_DIAGRAM));
            writeSection(groupDir, "04-components.md", sections.get(DocSection.COMPONENTS));
            writeSection(groupDir, "05-dependencies.md", sections.get(DocSection.DEPENDENCIES));
            writeSection(groupDir, "06-public-api.md", sections.get(DocSection.PUBLIC_API));
            writeSection(groupDir, "07-commits.md", sections.get(DocSection.COMMITS));
            writeSection(groupDir, "08-contributors.md", sections.get(DocSection.CONTRIBUTORS));
            writeSection(groupDir, "09-meta.md", sections.get(DocSection.META));

            log.info("Documentation service sauvegardée : {}", groupDir.toAbsolutePath());
            return new WriteResult.Success(groupDir, 0);

        } catch (Exception e) {
            log.error("Échec d'écriture du service {} : {}", serviceName, e.getMessage());
            return new WriteResult.Failed("Erreur d'écriture : " + e.getMessage());
        }
    }

    private void writeSection(Path dir, String fileName, List<DocFragment> fragments) throws Exception {
        if (fragments != null && !fragments.isEmpty()) {
            String content = fragmentRenderer.render(fragments);
            String serviceName = dir.getFileName().toString();
            String footer = "\n\n---\n";
            if ("README.md".equals(fileName)) {
                footer += "[💬 Discuter avec l'assistant IA sur ce composant](http://localhost:3000/?project_id=PFAProject&service=" + serviceName + ")\n\n";
            }

            Files.writeString(dir.resolve(fileName), content + footer, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    public WriteResult writeIndexDoc(String projectPath, List<DocFragment> fragments) {
        if (projectPath == null || projectPath.isBlank() || projectPath.equals("manual")) {
            return new WriteResult.Skipped("Chemin inconnu.");
        }

        Path root = Paths.get(projectPath);
        Path indexFile = root.resolve("rollo_docs").resolve("INDEX.md");

        try {
            Files.createDirectories(indexFile.getParent());
            writeSection(indexFile.getParent(), "INDEX.md", fragments);
            return new WriteResult.Success(indexFile, 0);
        } catch (Exception e) {
            log.error("Échec d'écriture de l'index : {}", e.getMessage());
            return new WriteResult.Failed("Erreur d'écriture index : " + e.getMessage());
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

        sb.append("\n\n---\n[💬 Discuter avec l'assistant IA sur ce projet](http://localhost:3000/?project_id=PFAProject)\n\n");

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
