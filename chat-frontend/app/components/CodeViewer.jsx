"use client";

import dynamic from 'next/dynamic';
import { useState, useEffect, useCallback } from 'react';
import MarkdownViewer from './MarkdownViewer';
import SymbolPanel from './SymbolPanel';
import { useTreeSitter } from '../hooks/useTreeSitter';

// Monaco doit être chargé côté client uniquement (pas de SSR)
const MonacoEditor = dynamic(
  () => import('@monaco-editor/react').then((mod) => mod.default),
  { ssr: false, loading: () => <EditorSkeleton /> }
);

function EditorSkeleton() {
  return (
    <div className="flex-1 flex items-center justify-center bg-[#1e1e1e]">
      <div className="flex flex-col items-center gap-3 text-slate-500">
        <div className="w-8 h-8 border-2 border-slate-600 border-t-purple-500 rounded-full animate-spin" />
        <span className="text-sm font-mono">Chargement de l&apos;éditeur...</span>
      </div>
    </div>
  );
}

function EmptyState({ onClose }) {
  return (
    <div className="flex-1 flex flex-col items-center justify-center bg-[#1e1e1e] text-slate-500 gap-4">
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
        strokeWidth={1} stroke="currentColor" className="w-16 h-16 opacity-20">
        <path strokeLinecap="round" strokeLinejoin="round"
          d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5" />
      </svg>
      <p className="text-sm">Cliquez sur un fichier dans la sidebar pour l&apos;ouvrir ici</p>
      <button
        onClick={onClose}
        className="text-xs text-slate-600 hover:text-slate-400 underline transition-colors"
      >
        Fermer le panneau
      </button>
    </div>
  );
}

/**
 * CodeViewer — Éditeur Monaco avec analyse AST Tree-sitter intégrée.
 *
 * Quand un fichier source est ouvert (Java, JS, TS, Python) :
 * 1. Monaco affiche le code avec coloration syntaxique
 * 2. web-tree-sitter parse le contenu en WASM côté client
 * 3. SymbolPanel affiche les symboles extraits (classes, méthodes, annotations...)
 * 4. Cliquer sur un symbole navigue vers sa ligne dans Monaco
 */
export default function CodeViewer({ filePath, theme, onClose, onAskAI }) {
  const [fileData, setFileData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [editorInstance, setEditorInstance] = useState(null);
  const [showSymbols, setShowSymbols] = useState(true);

  const isMarkdown = filePath && filePath.toLowerCase().endsWith('.md');

  // ── Chargement du fichier ──────────────────────────────────────────────────
  useEffect(() => {
    if (!filePath) {
      setFileData(null);
      return;
    }
    setLoading(true);
    setError(null);

    fetch(`/api/file?path=${encodeURIComponent(filePath)}`)
      .then((res) => res.json())
      .then((data) => {
        if (data.error) throw new Error(data.error);
        setFileData(data);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [filePath]);

  // ── Mapping langage Monaco → langage Tree-sitter ──────────────────────────
  const treeSitterLang = mapToTreeSitterLang(fileData?.language);

  // ── Analyse AST Tree-sitter (web-tree-sitter WASM) ────────────────────────
  const { symbols, loading: astLoading, error: astError, astReady } = useTreeSitter(
    fileData?.content || null,
    treeSitterLang
  );

  // ── Navigation vers une ligne dans Monaco ─────────────────────────────────
  const handleSymbolClick = useCallback((symbol) => {
    if (!editorInstance || !symbol.line) return;
    editorInstance.revealLineInCenter(symbol.line);
    editorInstance.setPosition({ lineNumber: symbol.line, column: 1 });
    editorInstance.focus();
  }, [editorInstance]);

  // ── Demander à l'IA d'expliquer un symbole (disponible via onAskAI prop) ──
  const handleAskAIAboutSymbol = useCallback((symbol) => { // eslint-disable-line no-unused-vars
    if (onAskAI) {
      onAskAI(`Explique le symbole \`${symbol.name}\` dans le fichier \`${fileData?.filename}\``);
    }
  }, [onAskAI, fileData]);

  const monacoTheme = theme === 'dark' ? 'vs-dark' : 'light';

  // Fichiers Markdown → viewer dédié avec support Mermaid
  if (isMarkdown && fileData && !loading && !error) {
    return (
      <MarkdownViewer
        content={fileData.content}
        filename={fileData.filename}
        dark={theme === 'dark'}
        onClose={onClose}
      />
    );
  }

  return (
    <div className="flex flex-col h-full bg-[#1e1e1e] border-l border-slate-700/50">

      {/* ── Tab bar ──────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between h-10 px-4 bg-[#252526] border-b border-slate-700/50 flex-shrink-0">
        <div className="flex items-center gap-2 min-w-0 flex-1">
          {fileData && (
            <span className="text-xs font-mono text-slate-300 truncate">
              {fileData.filename}
            </span>
          )}
          {!fileData && !loading && (
            <span className="text-xs text-slate-500 italic">Aucun fichier ouvert</span>
          )}
          {loading && (
            <span className="text-xs text-slate-500 italic">Chargement...</span>
          )}

          {/* Badge Tree-sitter actif */}
          {astReady && treeSitterLang && (
            <span className="text-[9px] px-1.5 py-0.5 rounded bg-green-900/30 text-green-400 border border-green-800/40 flex-shrink-0">
              🌳 Tree-sitter
            </span>
          )}
        </div>

        <div className="flex items-center gap-1 flex-shrink-0">
          {/* Toggle panneau symboles */}
          {treeSitterLang && fileData && (
            <button
              onClick={() => setShowSymbols(v => !v)}
              title={showSymbols ? "Masquer les symboles AST" : "Afficher les symboles AST"}
              className={`p-1.5 rounded transition-colors text-xs ${
                showSymbols
                  ? 'bg-purple-900/40 text-purple-400'
                  : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              ⬡
            </button>
          )}

          {/* Bouton fermer */}
          <button
            onClick={onClose}
            title="Fermer l'éditeur"
            className="text-slate-500 hover:text-slate-300 transition-colors p-1"
          >
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
              strokeWidth={2} stroke="currentColor" className="w-4 h-4">
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>

      {/* ── Zone principale : Monaco + SymbolPanel ────────────────────────── */}
      <div className="flex-1 flex flex-col overflow-hidden">

        {/* Monaco Editor */}
        <div className="flex-1 overflow-hidden">
          {error && (
            <div className="flex items-center justify-center h-full text-red-400 text-sm px-6 text-center">
              ⚠️ {error}
            </div>
          )}

          {!error && !filePath && <EmptyState onClose={onClose} />}

          {!error && filePath && !loading && fileData && (
            <MonacoEditor
              height="100%"
              language={fileData.language}
              value={fileData.content}
              theme={monacoTheme}
              onMount={(editor) => setEditorInstance(editor)}
              options={{
                readOnly: true,
                minimap: { enabled: true },
                fontSize: 13,
                fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
                fontLigatures: true,
                lineNumbers: 'on',
                scrollBeyondLastLine: false,
                wordWrap: 'off',
                automaticLayout: true,
                renderLineHighlight: 'line',
                smoothScrolling: true,
                cursorBlinking: 'smooth',
                padding: { top: 12, bottom: 12 },
              }}
            />
          )}

          {!error && filePath && loading && <EditorSkeleton />}
        </div>

        {/* ── SymbolPanel Tree-sitter ───────────────────────────────────── */}
        {showSymbols && fileData && treeSitterLang && (
          <SymbolPanel
            symbols={symbols}
            loading={astLoading}
            error={astError}
            astReady={astReady}
            language={treeSitterLang}
            onSymbolClick={(symbol) => {
              handleSymbolClick(symbol);
              // Double-clic → demander à l'IA
            }}
          />
        )}

      </div>
    </div>
  );
}

// ── Mapping langage Monaco → langage Tree-sitter ──────────────────────────────
function mapToTreeSitterLang(monacoLang) {
  if (!monacoLang) return null;
  const map = {
    java:       "java",
    javascript: "javascript",
    typescript: "typescript",
    python:     "python",
  };
  return map[monacoLang] || null;
}
