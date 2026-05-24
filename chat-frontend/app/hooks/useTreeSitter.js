"use client";

import { useState, useEffect, useRef } from "react";

/**
 * useTreeSitter — Hook React pour l'analyse AST avec web-tree-sitter.
 *
 * Charge web-tree-sitter depuis CDN (jsDelivr) sans dépendance npm.
 * Parse le contenu du fichier ouvert dans Monaco Editor en temps réel
 * et retourne les symboles extraits (classes, méthodes, fonctions, imports).
 *
 * Langages supportés : Java, JavaScript, TypeScript, Python
 *
 * @param {string|null} code     - Contenu du fichier à analyser
 * @param {string|null} language - Langage ('java', 'javascript', 'typescript', 'python')
 * @returns {{ symbols, loading, error, astReady }}
 */
export function useTreeSitter(code, language) {
  const [symbols, setSymbols]   = useState(null);
  const [loading, setLoading]   = useState(false);
  const [error,   setError]     = useState(null);
  const [astReady, setAstReady] = useState(false);

  const parserRef      = useRef(null);
  const currentLangRef = useRef(null);

  // URLs CDN — définies en dehors du useEffect pour stabilité des deps
  const GRAMMAR_URLS = useRef({
    java:       "https://cdn.jsdelivr.net/npm/tree-sitter-java@0.23.5/tree-sitter-java.wasm",
    javascript: "https://cdn.jsdelivr.net/npm/tree-sitter-javascript@0.23.1/tree-sitter-javascript.wasm",
    typescript: "https://cdn.jsdelivr.net/npm/tree-sitter-typescript@0.23.2/tree-sitter-typescript.wasm",
    python:     "https://cdn.jsdelivr.net/npm/tree-sitter-python@0.23.6/tree-sitter-python.wasm",
  }).current;

  // ── Chargement du script CDN (singleton) ──────────────────────────────────
  function loadScript(src) {
    return new Promise((resolve, reject) => {
      if (document.querySelector(`script[src="${src}"]`)) {
        resolve();
        return;
      }
      const s = document.createElement("script");
      s.src = src;
      s.onload = resolve;
      s.onerror = () => reject(new Error(`Impossible de charger : ${src}`));
      document.head.appendChild(s);
    });
  }

  // ── Initialisation parser + grammaire ─────────────────────────────────────
  useEffect(() => {
    if (!language || !GRAMMAR_URLS[language]) {      setSymbols(null);
      setAstReady(false);
      return;
    }

    // Même langage déjà chargé → réutiliser
    if (currentLangRef.current === language && parserRef.current) {
      setAstReady(true);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    setAstReady(false);

    async function init() {
      try {
        // 1. Charger le script web-tree-sitter depuis CDN
        await loadScript(CDN_CORE);

        if (cancelled) return;

        const TreeSitter = window.TreeSitter;
        if (!TreeSitter) throw new Error("window.TreeSitter introuvable après chargement CDN");

        // 2. Initialiser le moteur WASM
        await TreeSitter.init({
          locateFile: () => WASM_CORE,
        });

        if (cancelled) return;

        // 3. Charger la grammaire du langage
        const lang = await TreeSitter.Language.load(GRAMMAR_URLS[language]);

        if (cancelled) return;

        // 4. Créer le parser
        const parser = new TreeSitter();
        parser.setLanguage(lang);

        parserRef.current      = parser;
        currentLangRef.current = language;

        setAstReady(true);
        setLoading(false);

      } catch (err) {
        if (!cancelled) {
          console.warn("[Tree-sitter] Init échouée :", err.message);
          setError(err.message);
          setLoading(false);
        }
      }
    }

    init();
    return () => { cancelled = true; };
  }, [language]);

  // ── Parse du code quand le parser est prêt ────────────────────────────────
  useEffect(() => {
    if (!astReady || !parserRef.current || !code) {
      if (!code) setSymbols(null);
      return;
    }

    try {
      const tree = parserRef.current.parse(code);
      const extracted = extractSymbols(tree.rootNode, language);
      setSymbols(extracted);
    } catch (err) {
      console.warn("[Tree-sitter] Parse échoué :", err.message);
      setSymbols(null);
    }
  }, [code, astReady, language]);

  return { symbols, loading, error, astReady };
}

// ============================================================================
// EXTRACTION DE SYMBOLES depuis l'AST Tree-sitter
// ============================================================================

function extractSymbols(rootNode, language) {
  const symbols = {
    classes:     [],
    interfaces:  [],
    methods:     [],
    functions:   [],
    imports:     [],
    annotations: [],
  };

  switch (language) {
    case "java":       extractJavaSymbols(rootNode, symbols);   break;
    case "typescript":
    case "javascript": extractJsTsSymbols(rootNode, symbols);   break;
    case "python":     extractPythonSymbols(rootNode, symbols); break;
  }

  return symbols;
}

// ── Java ─────────────────────────────────────────────────────────────────────
function extractJavaSymbols(node, symbols) {
  if (!node) return;

  switch (node.type) {
    case "class_declaration":
    case "record_declaration": {
      const n = node.childForFieldName("name");
      if (n) symbols.classes.push(n.text);
      break;
    }
    case "interface_declaration": {
      const n = node.childForFieldName("name");
      if (n) symbols.interfaces.push(n.text);
      break;
    }
    case "method_declaration": {
      const name = node.childForFieldName("name");
      const ret  = node.childForFieldName("type");
      if (name) symbols.methods.push({
        name:       name.text,
        returnType: ret ? ret.text : "void",
        line:       node.startPosition.row + 1,
      });
      break;
    }
    case "import_declaration": {
      const text = node.text.replace(/^import\s+/, "").replace(/;$/, "").trim();
      symbols.imports.push(text);
      break;
    }
    case "annotation": {
      const n = node.childForFieldName("name");
      if (n && !symbols.annotations.includes(n.text)) symbols.annotations.push(n.text);
      break;
    }
  }

  for (let i = 0; i < node.childCount; i++) extractJavaSymbols(node.child(i), symbols);
}

// ── JavaScript / TypeScript ───────────────────────────────────────────────────
function extractJsTsSymbols(node, symbols) {
  if (!node) return;

  switch (node.type) {
    case "class_declaration":
    case "abstract_class_declaration": {
      const n = node.childForFieldName("name");
      if (n) symbols.classes.push(n.text);
      break;
    }
    case "interface_declaration": {
      const n = node.childForFieldName("name");
      if (n) symbols.interfaces.push(n.text);
      break;
    }
    case "function_declaration":
    case "generator_function_declaration": {
      const n = node.childForFieldName("name");
      if (n) symbols.functions.push({ name: n.text, line: node.startPosition.row + 1 });
      break;
    }
    case "method_definition": {
      const n = node.childForFieldName("name");
      if (n) symbols.methods.push({ name: n.text, line: node.startPosition.row + 1 });
      break;
    }
    case "import_statement": {
      const src = node.childForFieldName("source");
      if (src) symbols.imports.push(src.text.replace(/['"]/g, ""));
      break;
    }
    case "decorator": {
      const text = node.text.replace(/^@/, "").split("(")[0];
      if (!symbols.annotations.includes(text)) symbols.annotations.push(text);
      break;
    }
  }

  for (let i = 0; i < node.childCount; i++) extractJsTsSymbols(node.child(i), symbols);
}

// ── Python ────────────────────────────────────────────────────────────────────
function extractPythonSymbols(node, symbols) {
  if (!node) return;

  switch (node.type) {
    case "class_definition": {
      const n = node.childForFieldName("name");
      if (n) symbols.classes.push(n.text);
      break;
    }
    case "function_definition": {
      const n = node.childForFieldName("name");
      if (n) symbols.functions.push({ name: n.text, line: node.startPosition.row + 1 });
      break;
    }
    case "import_statement":
    case "import_from_statement": {
      symbols.imports.push(node.text.trim());
      break;
    }
    case "decorator": {
      const text = node.text.replace(/^@/, "").split("(")[0];
      if (!symbols.annotations.includes(text)) symbols.annotations.push(text);
      break;
    }
  }

  for (let i = 0; i < node.childCount; i++) extractPythonSymbols(node.child(i), symbols);
}
