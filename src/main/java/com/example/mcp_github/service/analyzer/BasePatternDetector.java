package com.example.mcp_github.service.analyzer;

import java.util.List;

/**
 * Classe de base contenant des utilitaires pour les détecteurs de patterns.
 */
public abstract class BasePatternDetector implements PatternDetector {

    /**
     * Détection de dossier insensible à la casse.
     */
    protected boolean hasFolder(List<String> paths, String folderName) {
        String lower = folderName.toLowerCase();
        return paths.stream().anyMatch(p -> {
            String lp = p.toLowerCase();
            return lp.contains("/" + lower + "/")
                    || lp.startsWith(lower + "/");
        });
    }

    /**
     * Détection de fichier insensible à la casse.
     */
    protected boolean hasFile(List<String> paths, String fileName) {
        String lower = fileName.toLowerCase();
        return paths.stream().anyMatch(p -> {
            String lp = p.toLowerCase();
            return lp.equals(lower) || lp.endsWith("/" + lower);
        });
    }

    protected String fileName(String path) {
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }
}
