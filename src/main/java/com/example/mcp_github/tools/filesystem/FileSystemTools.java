package com.example.mcp_github.tools.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_github.service.MemoryService;

/**
 * FileSystemTools — Serveur MCP Filesystem natif Spring AI.
 *
 * Implémente les opérations filesystem du protocole MCP directement dans
 * le backend Spring Boot, sans dépendance à un processus Node.js externe.
 *
 * Opérations exposées :
 * - readLocalFile      : lire le contenu d'un fichier local
 * - listLocalDirectory : lister le contenu d'un répertoire
 * - writeLocalFile     : écrire/créer un fichier local
 * - searchLocalFiles   : rechercher des fichiers par pattern
 * - getFileInfo        : métadonnées d'un fichier (taille, dates, type)
 *
 * Sécurité : toutes les opérations sont restreintes au workspace résolu
 * depuis la mémoire (current_path). Les tentatives de path traversal
 * (../) sont bloquées.
 */
@Component
public class FileSystemTools {

    private static final Logger log = LoggerFactory.getLogger(FileSystemTools.class);

    private static final long MAX_READ_SIZE = 512_000L; // 500 KB
    private static final int  MAX_LIST_DEPTH = 5;
    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", "target", ".next", ".mvn",
            "__pycache__", ".venv", "venv", "vendor", "dist", "build");

    private final MemoryService memoryService;

    public FileSystemTools(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    // =========================================================================
    // READ FILE
    // =========================================================================

    @Tool(name = "readLocalFile", description = """
            Lit le contenu d'un fichier local sur le système de fichiers du workspace.

            Utiliser cet outil pour :
            - Lire un fichier de configuration local (application.properties, pom.xml, etc.)
            - Inspecter le contenu d'un fichier source local (sans passer par GitHub)
            - Lire la documentation générée (rollo.md, rollo_detailled.md)

            Le chemin peut être absolu ou relatif au workspace courant.
            Taille maximale : 500 KB.
            """)
    public String readLocalFile(
            @ToolParam(description = "Chemin du fichier à lire. Absolu ou relatif au workspace.")
            String filePath) {

        log.info("TOOL: readLocalFile — path={}", filePath);

        try {
            Path resolved = resolveSafePath(filePath);
            if (resolved == null) {
                return "ERREUR : Chemin invalide ou accès refusé (path traversal détecté) : " + filePath;
            }

            if (!Files.exists(resolved)) {
                return "ERREUR : Fichier introuvable : " + resolved.toAbsolutePath();
            }
            if (Files.isDirectory(resolved)) {
                return "ERREUR : Le chemin pointe vers un répertoire, pas un fichier : " + resolved.toAbsolutePath()
                        + "\nUtilisez `listLocalDirectory` pour lister un répertoire.";
            }

            long size = Files.size(resolved);
            if (size > MAX_READ_SIZE) {
                return "ERREUR : Fichier trop grand (%d octets > 500 KB) : %s"
                        .formatted(size, resolved.toAbsolutePath());
            }

            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            return "# Contenu de `%s`\n\n```\n%s\n```".formatted(resolved.toAbsolutePath(), content);

        } catch (IOException e) {
            log.error("readLocalFile a échoué pour {}", filePath, e);
            return "ERREUR de lecture : " + e.getMessage();
        }
    }

    // =========================================================================
    // LIST DIRECTORY
    // =========================================================================

    @Tool(name = "listLocalDirectory", description = """
            Liste le contenu d'un répertoire local du workspace.

            Retourne l'arborescence des fichiers et sous-dossiers avec :
            - Type (fichier/dossier)
            - Taille des fichiers
            - Date de dernière modification

            Les dossiers ignorés automatiquement : node_modules, .git, target, .next, etc.
            Profondeur maximale : 5 niveaux.

            Utiliser cet outil pour explorer la structure locale du projet
            sans passer par l'API GitHub.
            """)
    public String listLocalDirectory(
            @ToolParam(description = "Chemin du répertoire à lister. Absolu ou relatif au workspace. Défaut : workspace racine.", required = false)
            String dirPath,
            @ToolParam(description = "Profondeur de récursion (1-5, défaut: 2)", required = false)
            Integer depth) {

        log.info("TOOL: listLocalDirectory — path={} depth={}", dirPath, depth);

        try {
            Path resolved;
            if (dirPath == null || dirPath.isBlank()) {
                String workspace = memoryService.recall("current_path");
                if (workspace == null || workspace.equals("manual")) {
                    return "ERREUR : Aucun workspace configuré. Lancez `initializeProject` d'abord.";
                }
                resolved = Paths.get(workspace);
            } else {
                resolved = resolveSafePath(dirPath);
                if (resolved == null) {
                    return "ERREUR : Chemin invalide ou accès refusé : " + dirPath;
                }
            }

            if (!Files.exists(resolved) || !Files.isDirectory(resolved)) {
                return "ERREUR : Répertoire introuvable : " + resolved.toAbsolutePath();
            }

            int maxDepth = (depth != null) ? Math.min(Math.max(depth, 1), MAX_LIST_DEPTH) : 2;

            StringBuilder sb = new StringBuilder();
            sb.append("# Répertoire : `").append(resolved.toAbsolutePath()).append("`\n\n");
            sb.append("```\n");
            buildTree(resolved, resolved, sb, 0, maxDepth);
            sb.append("```\n");

            return sb.toString();

        } catch (IOException e) {
            log.error("listLocalDirectory a échoué pour {}", dirPath, e);
            return "ERREUR : " + e.getMessage();
        }
    }

    // =========================================================================
    // WRITE FILE
    // =========================================================================

    @Tool(name = "writeLocalFile", description = """
            Écrit ou crée un fichier local dans le workspace.

            Modes disponibles :
            - CREATE   : crée un nouveau fichier (échoue si le fichier existe déjà)
            - OVERWRITE: remplace le contenu existant
            - APPEND   : ajoute au contenu existant

            Utiliser cet outil pour :
            - Créer des fichiers de documentation générés
            - Mettre à jour des fichiers de configuration
            - Sauvegarder des résultats d'analyse

            ATTENTION : opération irréversible en mode OVERWRITE.
            """)
    public String writeLocalFile(
            @ToolParam(description = "Chemin du fichier à écrire. Absolu ou relatif au workspace.")
            String filePath,
            @ToolParam(description = "Contenu à écrire dans le fichier.")
            String content,
            @ToolParam(description = "Mode d'écriture : CREATE, OVERWRITE, ou APPEND. Défaut : OVERWRITE.", required = false)
            String mode) {

        log.info("TOOL: writeLocalFile — path={} mode={}", filePath, mode);

        try {
            Path resolved = resolveSafePath(filePath);
            if (resolved == null) {
                return "ERREUR : Chemin invalide ou accès refusé : " + filePath;
            }

            // Créer les répertoires parents si nécessaire
            if (resolved.getParent() != null) {
                Files.createDirectories(resolved.getParent());
            }

            String writeMode = (mode != null) ? mode.toUpperCase() : "OVERWRITE";

            switch (writeMode) {
                case "CREATE" -> {
                    if (Files.exists(resolved)) {
                        return "ERREUR : Le fichier existe déjà (mode CREATE) : " + resolved.toAbsolutePath()
                                + "\nUtilisez mode=OVERWRITE pour remplacer.";
                    }
                    Files.writeString(resolved, content, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW);
                }
                case "APPEND" -> {
                    Files.writeString(resolved, content, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                default -> { // OVERWRITE
                    Files.writeString(resolved, content, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }

            long written = Files.size(resolved);
            return "✅ Fichier écrit avec succès (%d octets) : `%s`"
                    .formatted(written, resolved.toAbsolutePath());

        } catch (IOException e) {
            log.error("writeLocalFile a échoué pour {}", filePath, e);
            return "ERREUR d'écriture : " + e.getMessage();
        }
    }

    // =========================================================================
    // SEARCH FILES
    // =========================================================================

    @Tool(name = "searchLocalFiles", description = """
            Recherche des fichiers dans le workspace par nom ou extension.

            Exemples :
            - pattern="*.java"     → tous les fichiers Java
            - pattern="Service"    → fichiers dont le nom contient "Service"
            - pattern="*.md"       → tous les fichiers Markdown

            Retourne les chemins relatifs au workspace, triés alphabétiquement.
            Maximum 100 résultats.
            """)
    public String searchLocalFiles(
            @ToolParam(description = "Pattern de recherche : extension (*.java), nom partiel (Service), ou nom exact.")
            String pattern,
            @ToolParam(description = "Répertoire de départ. Défaut : workspace racine.", required = false)
            String startDir) {

        log.info("TOOL: searchLocalFiles — pattern={} startDir={}", pattern, startDir);

        try {
            Path root;
            if (startDir != null && !startDir.isBlank()) {
                root = resolveSafePath(startDir);
                if (root == null) return "ERREUR : Chemin invalide : " + startDir;
            } else {
                String workspace = memoryService.recall("current_path");
                if (workspace == null || workspace.equals("manual")) {
                    return "ERREUR : Aucun workspace configuré. Lancez `initializeProject` d'abord.";
                }
                root = Paths.get(workspace);
            }

            if (!Files.isDirectory(root)) {
                return "ERREUR : Répertoire introuvable : " + root.toAbsolutePath();
            }

            // Normaliser le pattern
            String normalizedPattern = pattern.trim();
            boolean isExtension = normalizedPattern.startsWith("*.");
            String matchStr = isExtension
                    ? normalizedPattern.substring(1).toLowerCase() // ".java"
                    : normalizedPattern.toLowerCase();

            List<String> matches = new ArrayList<>();
            Path finalRoot = root;

            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> !isInIgnoredDir(p, finalRoot))
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return isExtension ? name.endsWith(matchStr) : name.contains(matchStr);
                        })
                        .limit(100)
                        .map(p -> finalRoot.relativize(p).toString().replace("\\", "/"))
                        .sorted()
                        .forEach(matches::add);
            }

            if (matches.isEmpty()) {
                return "Aucun fichier trouvé pour le pattern `%s` dans `%s`."
                        .formatted(pattern, root.toAbsolutePath());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# Résultats de recherche : `%s`\n\n".formatted(pattern));
            sb.append("**%d fichier(s) trouvé(s)** dans `%s`\n\n".formatted(matches.size(), root.toAbsolutePath()));
            matches.forEach(m -> sb.append("- `").append(m).append("`\n"));
            return sb.toString();

        } catch (IOException e) {
            log.error("searchLocalFiles a échoué", e);
            return "ERREUR : " + e.getMessage();
        }
    }

    // =========================================================================
    // FILE INFO
    // =========================================================================

    @Tool(name = "getLocalFileInfo", description = """
            Retourne les métadonnées d'un fichier ou répertoire local :
            - Type (fichier/répertoire)
            - Taille en octets
            - Date de création et de dernière modification
            - Permissions (lecture/écriture)
            - Chemin absolu résolu

            Utile pour vérifier l'existence d'un fichier avant de le lire,
            ou pour obtenir des informations sur la documentation générée.
            """)
    public String getLocalFileInfo(
            @ToolParam(description = "Chemin du fichier ou répertoire. Absolu ou relatif au workspace.")
            String filePath) {

        log.info("TOOL: getLocalFileInfo — path={}", filePath);

        try {
            Path resolved = resolveSafePath(filePath);
            if (resolved == null) {
                return "ERREUR : Chemin invalide ou accès refusé : " + filePath;
            }

            if (!Files.exists(resolved)) {
                return "❌ Fichier/répertoire introuvable : `%s`".formatted(resolved.toAbsolutePath());
            }

            BasicFileAttributes attrs = Files.readAttributes(resolved, BasicFileAttributes.class);
            boolean isDir = attrs.isDirectory();
            long size = attrs.size();
            Instant created = attrs.creationTime().toInstant();
            Instant modified = attrs.lastModifiedTime().toInstant();

            StringBuilder sb = new StringBuilder();
            sb.append("# Informations : `%s`\n\n".formatted(resolved.toAbsolutePath()));
            sb.append("| Propriété | Valeur |\n|-----------|--------|\n");
            sb.append("| Type | %s |\n".formatted(isDir ? "📁 Répertoire" : "📄 Fichier"));
            if (!isDir) {
                sb.append("| Taille | %s |\n".formatted(formatSize(size)));
            }
            sb.append("| Créé le | %s |\n".formatted(created));
            sb.append("| Modifié le | %s |\n".formatted(modified));
            sb.append("| Lecture | %s |\n".formatted(Files.isReadable(resolved) ? "✅" : "❌"));
            sb.append("| Écriture | %s |\n".formatted(Files.isWritable(resolved) ? "✅" : "❌"));

            if (isDir) {
                try (Stream<Path> entries = Files.list(resolved)) {
                    long count = entries.count();
                    sb.append("| Entrées | %d |\n".formatted(count));
                }
            }

            return sb.toString();

        } catch (IOException e) {
            log.error("getLocalFileInfo a échoué pour {}", filePath, e);
            return "ERREUR : " + e.getMessage();
        }
    }

    // =========================================================================
    // UTILITAIRES PRIVÉS
    // =========================================================================

    /**
     * Résout un chemin de manière sécurisée.
     * - Si absolu : vérifie qu'il est dans le workspace
     * - Si relatif : résout par rapport au workspace
     * - Bloque les path traversal (..)
     *
     * @return le chemin résolu, ou null si accès refusé
     */
    private Path resolveSafePath(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;

        String workspace = memoryService.recall("current_path");
        Path workspacePath = (workspace != null && !workspace.equals("manual"))
                ? Paths.get(workspace).toAbsolutePath().normalize()
                : null;

        Path input = Paths.get(filePath);
        Path resolved;

        if (input.isAbsolute()) {
            resolved = input.normalize();
        } else if (workspacePath != null) {
            resolved = workspacePath.resolve(filePath).normalize();
        } else {
            // Pas de workspace — on accepte les chemins absolus uniquement
            return null;
        }

        // Sécurité : vérifier que le chemin résolu est dans le workspace
        if (workspacePath != null && !resolved.startsWith(workspacePath)) {
            log.warn("Path traversal bloqué : {} → {} (hors workspace {})",
                    filePath, resolved, workspacePath);
            return null;
        }

        return resolved;
    }

    private void buildTree(Path root, Path current, StringBuilder sb, int depth, int maxDepth)
            throws IOException {
        if (depth > maxDepth) return;

        String indent = "  ".repeat(depth);
        try (Stream<Path> entries = Files.list(current).sorted()) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (isInIgnoredDir(entry, root)) continue;

                if (Files.isDirectory(entry)) {
                    sb.append(indent).append("📁 ").append(name).append("/\n");
                    if (depth < maxDepth) {
                        buildTree(root, entry, sb, depth + 1, maxDepth);
                    }
                } else {
                    long size = Files.size(entry);
                    sb.append(indent).append("📄 ").append(name)
                            .append(" (").append(formatSize(size)).append(")\n");
                }
            }
        }
    }

    private boolean isInIgnoredDir(Path path, Path root) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (IGNORED_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }
}
