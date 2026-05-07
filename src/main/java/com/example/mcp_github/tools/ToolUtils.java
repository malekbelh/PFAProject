package com.example.mcp_github.tools;

/**
 * Chaque Tool class peut désormais appeler ToolUtils.resolveLimit(limit).
 */
public final class ToolUtils {

    private ToolUtils() {
        // classe utilitaire — non instanciable
    }

    /**
     * Normalise le paramètre limit fourni par l'utilisateur : - null ou <= 0  → valeur par défaut (10)
     * - > 100 → plafonné à 100 (limite GitHub)
     */
    public static int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 10;
        }
        return Math.min(limit, 100);
    }
}
