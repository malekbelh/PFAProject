"use client";

import { useEffect, useRef, useState } from "react";

// ── Mermaid loader (CDN, singleton) ──────────────────────────────────────────
let _mermaidReady = false;
let _mermaidPromise = null;

function loadMermaid(dark) {
  if (_mermaidReady) {
    window.mermaid.initialize({ startOnLoad: false, theme: dark ? "dark" : "default", securityLevel: "loose" });
    return Promise.resolve(window.mermaid);
  }
  if (_mermaidPromise) return _mermaidPromise;

  _mermaidPromise = new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js";
    s.onload = () => {
      _mermaidReady = true;
      window.mermaid.initialize({ startOnLoad: false, theme: dark ? "dark" : "default", securityLevel: "loose" });
      resolve(window.mermaid);
    };
    s.onerror = reject;
    document.head.appendChild(s);
  });
  return _mermaidPromise;
}

let _diagramId = 0;

function MermaidBlock({ code, dark }) {
  const [svg, setSvg] = useState(null);
  const [err, setErr] = useState(null);
  const id = useRef(`md-mermaid-${++_diagramId}`).current;

  useEffect(() => {
    let cancelled = false;
    loadMermaid(dark)
      .then((m) => m.render(id, code))
      .then(({ svg: s }) => { if (!cancelled) { setSvg(s); setErr(null); } })
      .catch((e) => { if (!cancelled) setErr(String(e)); });
    return () => { cancelled = true; };
  }, [code, dark, id]);

  if (err) return (
    <div className="my-3 rounded-lg border border-red-500/30 bg-red-950/20 p-3 text-xs text-red-400 font-mono overflow-x-auto">
      ⚠️ Mermaid error — {err}
      <pre className="mt-2 text-slate-400 whitespace-pre-wrap">{code}</pre>
    </div>
  );

  if (!svg) return (
    <div className="my-3 flex items-center gap-2 text-slate-500 text-sm p-4">
      <div className="w-4 h-4 border-2 border-slate-600 border-t-purple-500 rounded-full animate-spin" />
      Rendu du diagramme…
    </div>
  );

  return (
    <div
      className="my-4 rounded-xl border border-slate-200 dark:border-slate-700/50 bg-white dark:bg-slate-900/50 p-4 overflow-x-auto shadow-sm"
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}

// ── Inline markdown → React nodes ────────────────────────────────────────────
function renderInline(text) {
  const parts = [];
  const rx = /(\*\*.*?\*\*|`[^`]+`|\[.*?\]\(.*?\))/g;
  let last = 0, m;
  while ((m = rx.exec(text)) !== null) {
    if (m.index > last) parts.push(text.slice(last, m.index));
    const tok = m[0];
    if (tok.startsWith("**")) {
      parts.push(<strong key={m.index} className="font-semibold text-purple-600 dark:text-purple-300">{tok.slice(2, -2)}</strong>);
    } else if (tok.startsWith("`")) {
      parts.push(<code key={m.index} className="px-1.5 py-0.5 bg-slate-200 dark:bg-slate-800 text-purple-600 dark:text-purple-300 rounded font-mono text-[0.85em]">{tok.slice(1, -1)}</code>);
    } else {
      const lm = tok.match(/\[(.*?)\]\((.*?)\)/);
      if (lm) parts.push(<a key={m.index} href={lm[2]} target="_blank" rel="noreferrer" className="text-blue-500 hover:underline">{lm[1]}</a>);
    }
    last = m.index + tok.length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

// ── Table parser ─────────────────────────────────────────────────────────────
function parseTable(lines) {
  const rows = lines.map(l => l.replace(/^\||\|$/g, "").split("|").map(c => c.trim()));
  const [head, , ...body] = rows;
  return (
    <div className="my-4 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-700/50 shadow-sm">
      <table className="w-full text-sm border-collapse">
        <thead className="bg-slate-100 dark:bg-slate-800">
          <tr>{head.map((h, i) => <th key={i} className="px-4 py-2 text-left font-semibold text-slate-700 dark:text-slate-200 border-b border-slate-200 dark:border-slate-700">{renderInline(h)}</th>)}</tr>
        </thead>
        <tbody>
          {body.map((row, ri) => (
            <tr key={ri} className="border-b border-slate-100 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors">
              {row.map((cell, ci) => <td key={ci} className="px-4 py-2 text-slate-700 dark:text-slate-300">{renderInline(cell)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ── Main renderer ─────────────────────────────────────────────────────────────
function renderMarkdownAST(text, dark) {
  const lines = text.split("\n");
  const nodes = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // ── Fenced code block (``` ... ```) ──────────────────────────────────────
    const fenceMatch = line.match(/^```(\w*)/);
    if (fenceMatch) {
      const lang = fenceMatch[1].toLowerCase();
      const codeLines = [];
      i++;
      while (i < lines.length && !lines[i].startsWith("```")) {
        codeLines.push(lines[i]);
        i++;
      }
      i++; // skip closing ```
      const code = codeLines.join("\n");

      if (lang === "mermaid") {
        nodes.push(<MermaidBlock key={i} code={code.trim()} dark={dark} />);
      } else {
        nodes.push(
          <div key={i} className="my-3 rounded-lg overflow-hidden border border-slate-700/50 shadow-md">
            <div className="bg-slate-800 text-slate-300 px-3 py-1.5 text-xs font-mono border-b border-slate-700/50">
              {lang || "code"}
            </div>
            <pre className="p-4 bg-slate-900 text-slate-100 overflow-x-auto text-sm font-mono leading-relaxed">
              <code>{code}</code>
            </pre>
          </div>
        );
      }
      continue;
    }

    // ── Table ─────────────────────────────────────────────────────────────────
    if (line.startsWith("|") && i + 1 < lines.length && lines[i + 1].match(/^\|[-| :]+\|/)) {
      const tableLines = [];
      while (i < lines.length && lines[i].startsWith("|")) {
        tableLines.push(lines[i]);
        i++;
      }
      nodes.push(<div key={i}>{parseTable(tableLines)}</div>);
      continue;
    }

    // ── Headings ──────────────────────────────────────────────────────────────
    if (line.startsWith("#### ")) { nodes.push(<h4 key={i} className="text-sm font-bold mt-3 mb-1 text-slate-700 dark:text-slate-300">{renderInline(line.slice(5))}</h4>); i++; continue; }
    if (line.startsWith("### "))  { nodes.push(<h3 key={i} className="text-base font-bold mt-4 mb-1 text-slate-800 dark:text-slate-200">{renderInline(line.slice(4))}</h3>); i++; continue; }
    if (line.startsWith("## "))   { nodes.push(<h2 key={i} className="text-lg font-bold mt-5 mb-2 text-purple-700 dark:text-purple-400 border-b border-slate-200 dark:border-slate-700 pb-1">{renderInline(line.slice(3))}</h2>); i++; continue; }
    if (line.startsWith("# "))    { nodes.push(<h1 key={i} className="text-2xl font-bold mt-6 mb-3 text-purple-800 dark:text-purple-300">{renderInline(line.slice(2))}</h1>); i++; continue; }

    // ── Horizontal rule ───────────────────────────────────────────────────────
    if (line.match(/^---+$/) || line.match(/^\*\*\*+$/)) { nodes.push(<hr key={i} className="my-4 border-slate-200 dark:border-slate-700" />); i++; continue; }

    // ── Blockquote ────────────────────────────────────────────────────────────
    if (line.startsWith("> ")) { nodes.push(<blockquote key={i} className="border-l-4 border-purple-500 pl-4 py-1 my-2 bg-slate-100 dark:bg-slate-800/50 italic text-slate-600 dark:text-slate-400">{renderInline(line.slice(2))}</blockquote>); i++; continue; }

    // ── Checkbox list ─────────────────────────────────────────────────────────
    if (line.match(/^- \[[ x]\]/)) {
      const checked = line.includes("- [x]");
      const label = line.replace(/^- \[[ x]\]\s*/, "");
      nodes.push(
        <div key={i} className="flex items-start gap-2 my-1 ml-2">
          <input type="checkbox" readOnly checked={checked} className="mt-1 accent-purple-500" />
          <span className={`text-sm ${checked ? "line-through text-slate-400" : "text-slate-700 dark:text-slate-300"}`}>{renderInline(label)}</span>
        </div>
      );
      i++; continue;
    }

    // ── Unordered list ────────────────────────────────────────────────────────
    if (line.match(/^[-*+] /)) {
      const items = [];
      while (i < lines.length && lines[i].match(/^[-*+] /)) {
        items.push(<li key={i} className="my-0.5">{renderInline(lines[i].slice(2))}</li>);
        i++;
      }
      nodes.push(<ul key={i} className="list-disc ml-6 my-2 text-slate-700 dark:text-slate-300 text-sm space-y-0.5">{items}</ul>);
      continue;
    }

    // ── Ordered list ──────────────────────────────────────────────────────────
    if (line.match(/^\d+\. /)) {
      const items = [];
      while (i < lines.length && lines[i].match(/^\d+\. /)) {
        items.push(<li key={i} className="my-0.5">{renderInline(lines[i].replace(/^\d+\. /, ""))}</li>);
        i++;
      }
      nodes.push(<ol key={i} className="list-decimal ml-6 my-2 text-slate-700 dark:text-slate-300 text-sm space-y-0.5">{items}</ol>);
      continue;
    }

    // ── Empty line ────────────────────────────────────────────────────────────
    if (!line.trim()) { nodes.push(<div key={i} className="h-2" />); i++; continue; }

    // ── Paragraph ─────────────────────────────────────────────────────────────
    nodes.push(<p key={i} className="text-sm leading-relaxed text-slate-700 dark:text-slate-300 my-1">{renderInline(line)}</p>);
    i++;
  }

  return nodes;
}

// ── Public component ──────────────────────────────────────────────────────────
export default function MarkdownViewer({ content, filename, dark, onClose }) {
  return (
    <div className="flex flex-col h-full bg-white dark:bg-[#0f172a] border-l border-slate-700/50">
      {/* Tab bar */}
      <div className="flex items-center justify-between h-10 px-4 bg-slate-100 dark:bg-[#252526] border-b border-slate-200 dark:border-slate-700/50 flex-shrink-0">
        <span className="text-xs font-mono text-slate-600 dark:text-slate-300 truncate">{filename}</span>
        <button onClick={onClose} title="Fermer" className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors ml-2 flex-shrink-0">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-4 h-4">
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Rendered markdown */}
      <div className="flex-1 overflow-y-auto p-6 max-w-4xl mx-auto w-full">
        {renderMarkdownAST(content, dark)}
      </div>
    </div>
  );
}
