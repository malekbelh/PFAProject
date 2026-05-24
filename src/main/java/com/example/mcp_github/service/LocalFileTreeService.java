package com.example.mcp_github.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.mcp_github.service.GitHubFileTreeService.RepositorySnapshot;
import com.example.mcp_github.service.GitHubFileTreeService.TreeEntry;

/**
 * LocalFileTreeService — Lecture du filesystem local pour la génération de doc.
 *
 * Alternative à GitHubFileTreeService quand le projet est disponible localement.
 *
 * Avantages vs API GitHub :
 * - Pas de limite de 25 fichiers
 * - Pas de token GitHub requis
 * - Lecture rapide depuis le disque
 * - Fonctionne hors-ligne
 *
 * Utilisé par RolloDocsBuilder quand current_path est accessible sur le disque.
 */
@Service
public class LocalFileTreeService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileTreeService.class);

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".ts", ".tsx", ".js", ".jsx",
            ".py", ".go", ".rs", ".cs", ".cpp", ".cc", ".h", ".hpp", ".rb");

    private static final Set<String> CONFIG_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "package.json", "requirements.txt", "setup.py", "pyproject.toml",
            "go.mod", "Cargo.toml", "Gemfile");

    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", "target", "dist", "build",
            ".mvn", "__pycache__", ".venv", "venv", "vendor",
            "coverage", ".idea", ".vscode", ".next");

    private static final long MAX_FILE_SIZE = 512_000L; // 500 KB

    // =========================================================================
    // API PUBLIQUE
    // =========================================================================

    /**
     * Vérifie si un chemin local est accessible sur le disque.
     */
    public boolean isLocalPathAvailable(String projectPath) {
        if (projectPath == null || projectPath.isBlank() || projectPath.equals("manual")) {
            return false;
        }
        return Files.isDirectory(Paths.get(projectPath));
    }

    /**
     * Construit un RepositorySnapshot depuis le filesystem local.
     * Équivalent de GitHubFileTreeService.snapshot() mais sans API GitHub.
     *
     * @param projectPath chemin absolu du projet sur le disque
     * @param owner       owner GitHub (pour compatibilité avec RepositorySnapshot)
     * @param repo        nom du repo (pour compatibilité)
     * @param branch      branche courante (pour compatibilité)
     */
    public RepositorySnapshot snapshotFromLocal(
            String projectPath, String owner, String repo, String branch) {

        log.info("📁 Lecture locale du projet : {}", projectPath);

        Path root = Paths.get(projectPath);

        // ── 1. Arborescence complète ──────────────────────────────────────────
        List<TreeEntry> tree = buildFileTree(root);
        log.info("📁 {} fichiers trouvés localement dans {}", tree.size(), projectPath);

        // ── 2. Contenu des fichiers source + config ───────────────────────────
        Map<String, String> keyFiles = readKeyFiles(root, tree);
        log.info("📄 {} fichiers lus pour l'analyse AST", keyFiles.size());

        return new RepositorySnapshot(owner, repo, branch, tree, keyFiles);
    }

    /**
     * Lit le contenu d'un fichier spécifique depuis le disque local.
     *
     * @param projectPath chemin absolu du projet
     * @param relativePath chemin relatif du fichier dans le projet
     */
    public String readLocalFile(String projectPath, String relativePath) {
        try {
            Path filePath = Paths.get(projectPath).resolve(relativePath).normalize();
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) return null;
            if (Files.size(filePath) > MAX_FILE_SIZE) {
                log.debug("Fichier trop grand ignoré : {}", relativePath);
                return null;
            }
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Impossible de lire {} : {}", relativePath, e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // PRIVÉ
    // =========================================================================

    /**
     * Parcourt récursivement le répertoire et retourne la liste des fichiers
     * sous forme de TreeEntry (compatible avec RepositorySnapshot).
     */
    private List<TreeEntry> buildFileTree(Path root) {
        List<TreeEntry> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !isInIgnoredDir(p, root))
                    .forEach(p -> {
                        String relative = root.relativize(p)
                                .toString()
                                .replace("\\", "/"); // normaliser les séparateurs Windows
                        try {
                            long size = Files.size(p);
                            entries.add(new TreeEntry(relative, "blob", null, size));
                        } catch (IOException e) {
                            entries.add(new TreeEntry(relative, "blob", null, 0L));
                        }
                    });
        } catch (IOException e) {
            log.error("Erreur lors du parcours de {} : {}", root, e.getMessage());
        }
        return entries;
    }

    /**
     * Lit le contenu des fichiers pertinents :
     * - Tous les fichiers de configuration (pom.xml, package.json...)
     * - Tous les fichiers source (sans limite de 25 contrairement à l'API GitHub)
     */
    private Map<String, String> readKeyFiles(Path root, List<TreeEntry> tree) {
        Map<String, String> result = new LinkedHashMap<>();

        // D'abord les fichiers de config
        tree.stream()
                .filter(e -> isConfigFile(e.path()))
                .forEach(e -> {
                    String content = readFile(root, e.path());
                    if (content != null) result.put(e.path(), content);
                });

        // Ensuite tous les fichiers source (pas de limite ici — avantage local)
        tree.stream()
                .filter(e -> isSourceFile(e.path()))
                .filter(e -> !isConfigFile(e.path()))
                .forEach(e -> {
                    String content = readFile(root, e.path());
                    if (content != null) result.put(e.path(), content);
                });

        return result;
    }

    private String readFile(Path root, String relativePath) {
        try {
            Path filePath = root.resolve(relativePath);
            if (Files.size(filePath) > MAX_FILE_SIZE) return null;
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Fichier ignoré : {} ({})", relativePath, e.getMessage());
            return null;
        }
    }

    private boolean isInIgnoredDir(Path path, Path root) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (IGNORED_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    private boolean isSourceFile(String path) {
        return SOURCE_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private boolean isConfigFile(String path) {
        String fileName = path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1) : path;
        return CONFIG_FILES.contains(fileName);
    }
}
