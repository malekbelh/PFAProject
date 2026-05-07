package com.example.mcp_github.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceResolver.class);

    private final org.springframework.boot.ApplicationArguments args;

    public WorkspaceResolver(org.springframework.beans.factory.ObjectProvider<org.springframework.boot.ApplicationArguments> argsProvider) {
        this.args = argsProvider.getIfAvailable();
    }

    /**
     * Résout le chemin du workspace actif selon l'ordre de priorité : 1.
     * Variable d'environnement PROJECT_PATH (priorité maximale — set par les
     * extensions IDE) 2. Variable d'environnement VSCODE_WORKSPACE (fallback VS
     * Code) 3. Propriété système user.dir (répertoire courant — dernier
     * recours)
     *
     * @return le chemin résolu, ou null si aucun n'est disponible
     */
    public String resolve() {
        // 0. Arguments de ligne de commande (ex: --workspace=/path/to/project)
        if (args != null && args.containsOption("workspace")) {
            String path = args.getOptionValues("workspace").get(0);
            if (isValid(path)) {
                log.debug("Workspace résolu via --workspace arg : {}", path);
                return path;
            }
        }

        // 1. PROJECT_PATH
        String path = System.getenv("PROJECT_PATH");
        if (isValid(path)) {
            log.debug("Workspace résolu via PROJECT_PATH : {}", path);
            return path;
        }

        // 2. VSCODE_WORKSPACE ou ANTIGRAVITY_WORKSPACE, etc.
        path = System.getenv("VSCODE_WORKSPACE");
        if (isValid(path)) {
            log.debug("Workspace résolu via VSCODE_WORKSPACE : {}", path);
            return path;
        }

        path = System.getenv("ANTIGRAVITY_WORKSPACE");
        if (isValid(path)) {
            log.debug("Workspace résolu via ANTIGRAVITY_WORKSPACE : {}", path);
            return path;
        }

        path = System.getenv("WORKSPACE_DIR");
        if (isValid(path)) {
            log.debug("Workspace résolu via WORKSPACE_DIR : {}", path);
            return path;
        }

        // 3. Fallback sur user.dir (le répertoire depuis lequel le serveur MCP a été lancé)
        // ATTENTION: Ceci provoquera la détection du serveur MCP lui-même s'il
        // est lancé depuis son propre code source.
        path = System.getProperty("user.dir");
        if (isValid(path)) {
            log.info("Aucune variable d'environnement détectée — utilisation du répertoire courant (user.dir) : {}", path);
            return path;
        }

        log.warn("Aucun chemin de workspace disponible.");
        return null;
    }

    private boolean isValid(String path) {
        return path != null && !path.isBlank();
    }
}
