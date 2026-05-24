"use client";

/**
 * SymbolPanel — Panneau d'affichage des symboles extraits par Tree-sitter.
 *
 * Affiche en temps réel les classes, méthodes, fonctions, imports et
 * annotations détectés par l'analyse AST web-tree-sitter du fichier
 * ouvert dans Monaco Editor.
 *
 * Inspiré du panneau "Outline" de VS Code / CodeWiki de Google.
 */
export default function SymbolPanel({ symbols, loading, error, astReady, language, onSymbolClick }) {

  if (!language || !["java", "javascript", "typescript", "python"].includes(language)) {
    return null; // Pas de panel pour les langages non supportés
  }

  return (
    <div className="border-t border-slate-700/50 bg-[#1e1e2e] flex flex-col max-h-64 flex-shrink-0">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-[#252540] border-b border-slate-700/50">
        <div className="flex items-center gap-2">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
            🌳 Tree-sitter — Symboles AST
          </span>
          {loading && (
            <div className="w-3 h-3 border border-slate-600 border-t-purple-400 rounded-full animate-spin" />
          )}
          {astReady && !loading && (
            <span className="text-[9px] px-1.5 py-0.5 rounded bg-green-900/40 text-green-400 border border-green-800/50">
              ✓ AST actif
            </span>
          )}
          {error && (
            <span className="text-[9px] px-1.5 py-0.5 rounded bg-red-900/40 text-red-400 border border-red-800/50">
              ⚠ WASM indisponible
            </span>
          )}
        </div>
        <span className="text-[9px] text-slate-500 font-mono">{language}</span>
      </div>

      {/* Contenu */}
      <div className="flex-1 overflow-y-auto px-2 py-1.5 space-y-1">

        {/* État de chargement */}
        {loading && (
          <div className="text-xs text-slate-500 italic px-2 py-2">
            Chargement de la grammaire Tree-sitter ({language})…
          </div>
        )}

        {/* Erreur WASM */}
        {error && !loading && (
          <div className="text-xs text-slate-500 px-2 py-2">
            <span className="text-amber-500">⚠</span> Grammaire WASM non disponible hors-ligne.
            <br />
            <span className="text-slate-600">L'analyse AST nécessite une connexion internet.</span>
          </div>
        )}

        {/* Aucun symbole */}
        {astReady && !loading && !error && symbols && isEmptySymbols(symbols) && (
          <div className="text-xs text-slate-600 italic px-2 py-2">
            Aucun symbole détecté dans ce fichier.
          </div>
        )}

        {/* Symboles extraits */}
        {astReady && !loading && !error && symbols && !isEmptySymbols(symbols) && (
          <div className="space-y-2">

            {/* Classes */}
            {symbols.classes.length > 0 && (
              <SymbolGroup
                icon="C"
                iconColor="text-yellow-400 bg-yellow-900/30"
                label="Classes"
                items={symbols.classes.map(c => ({ name: c }))}
                onItemClick={onSymbolClick}
              />
            )}

            {/* Interfaces */}
            {symbols.interfaces.length > 0 && (
              <SymbolGroup
                icon="I"
                iconColor="text-blue-400 bg-blue-900/30"
                label="Interfaces"
                items={symbols.interfaces.map(i => ({ name: i }))}
                onItemClick={onSymbolClick}
              />
            )}

            {/* Méthodes */}
            {symbols.methods.length > 0 && (
              <SymbolGroup
                icon="M"
                iconColor="text-purple-400 bg-purple-900/30"
                label="Méthodes"
                items={symbols.methods}
                onItemClick={onSymbolClick}
                showLine
              />
            )}

            {/* Fonctions */}
            {symbols.functions.length > 0 && (
              <SymbolGroup
                icon="ƒ"
                iconColor="text-green-400 bg-green-900/30"
                label="Fonctions"
                items={symbols.functions}
                onItemClick={onSymbolClick}
                showLine
              />
            )}

            {/* Annotations */}
            {symbols.annotations.length > 0 && (
              <SymbolGroup
                icon="@"
                iconColor="text-orange-400 bg-orange-900/30"
                label="Annotations"
                items={symbols.annotations.map(a => ({ name: a }))}
                onItemClick={onSymbolClick}
              />
            )}

            {/* Imports (limités à 5) */}
            {symbols.imports.length > 0 && (
              <SymbolGroup
                icon="↗"
                iconColor="text-slate-400 bg-slate-800"
                label={`Imports (${symbols.imports.length})`}
                items={symbols.imports.slice(0, 5).map(i => ({ name: i }))}
                onItemClick={onSymbolClick}
                muted
              />
            )}

          </div>
        )}

      </div>
    </div>
  );
}

// ── Groupe de symboles ────────────────────────────────────────────────────────
function SymbolGroup({ icon, iconColor, label, items, onItemClick, showLine = false, muted = false }) {
  return (
    <div>
      <div className="text-[9px] font-semibold text-slate-500 uppercase tracking-wider px-1 mb-0.5">
        {label}
      </div>
      <div className="space-y-0.5">
        {items.map((item, i) => (
          <button
            key={i}
            onClick={() => onItemClick && onItemClick(item)}
            className={`w-full flex items-center gap-2 px-2 py-0.5 rounded text-left hover:bg-slate-700/40 transition-colors group ${muted ? "opacity-60" : ""}`}
          >
            <span className={`text-[9px] font-bold w-4 h-4 flex items-center justify-center rounded flex-shrink-0 ${iconColor}`}>
              {icon}
            </span>
            <span className="text-xs font-mono text-slate-300 group-hover:text-white truncate flex-1">
              {item.name}
              {item.returnType && (
                <span className="text-slate-500 ml-1">: {item.returnType}</span>
              )}
            </span>
            {showLine && item.line && (
              <span className="text-[9px] text-slate-600 flex-shrink-0">L{item.line}</span>
            )}
          </button>
        ))}
      </div>
    </div>
  );
}

function isEmptySymbols(symbols) {
  return (
    symbols.classes.length === 0 &&
    symbols.interfaces.length === 0 &&
    symbols.methods.length === 0 &&
    symbols.functions.length === 0 &&
    symbols.annotations.length === 0 &&
    symbols.imports.length === 0
  );
}
