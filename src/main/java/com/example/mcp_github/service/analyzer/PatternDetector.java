package com.example.mcp_github.service.analyzer;

import java.util.List;
import com.example.mcp_github.service.ProjectStructureAnalyzer.ArchitecturePattern;
import com.example.mcp_github.service.ProjectStructureAnalyzer.PatternScore;

/**
 * Interface pour la stratégie de détection d'un pattern architectural.
 */
public interface PatternDetector {
    /**
     * Calcule le score de correspondance pour un pattern spécifique.
     * @param paths Liste des chemins de fichiers du projet.
     * @return Un objet PatternScore contenant le score et les signaux détectés.
     */
    PatternScore score(List<String> paths);

    /**
     * Retourne le pattern géré par ce détecteur.
     */
    ArchitecturePattern getPattern();
}
