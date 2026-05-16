"use client";

import React, { useState, useEffect, useRef } from 'react';

// Icons collection for UI without external dependencies
const Icons = {
  Sun: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5"><path strokeLinecap="round" strokeLinejoin="round" d="M12 3v2.25m6.364.386l-1.591 1.591M21 12h-2.25m-.386 6.364l-1.591-1.591M12 18.75V21m-4.773-4.227l-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0z" /></svg>,
  Moon: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5"><path strokeLinecap="round" strokeLinejoin="round" d="M21.752 15.002A9.718 9.718 0 0118 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 003 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 009.002-5.998z" /></svg>,
  Folder: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4"><path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" /></svg>,
  File: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4"><path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" /></svg>,
  Search: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4"><path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" /></svg>,
  Send: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5"><path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" /></svg>,
  Bot: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6"><path strokeLinecap="round" strokeLinejoin="round" d="M8.25 3v1.5M4.5 8.25H3m18 0h-1.5M4.5 12H3m18 0h-1.5m-15 3.75H3m18 0h-1.5M8.25 19.5V21M12 3v1.5m0 15V21m3.75-18v1.5m0 15V21m-9-1.5h10.5a2.25 2.25 0 002.25-2.25V6.75a2.25 2.25 0 00-2.25-2.25H6.75A2.25 2.25 0 004.5 6.75v10.5a2.25 2.25 0 002.25 2.25zm.75-12h9v9h-9v-9z" /></svg>,
  User: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5"><path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" /></svg>,
  Sparkles: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4"><path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.428-1.428L13.5 18.75l1.178-.394a2.25 2.25 0 001.428-1.428L16.5 15.75l.394 1.178a2.25 2.25 0 001.428 1.428l1.178.394-1.178.394a2.25 2.25 0 00-1.428 1.428z" /></svg>,
  Link: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-3 h-3"><path strokeLinecap="round" strokeLinejoin="round" d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m13.35-.622l1.757-1.757a4.5 4.5 0 00-6.364-6.364l-4.5 4.5a4.5 4.5 0 001.242 7.244" /></svg>,
  Terminal: () => <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-3 h-3"><path strokeLinecap="round" strokeLinejoin="round" d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
};

// Simple Markdown Parser for Rich Responses
const renderMarkdown = (text) => {
  if (!text) return null;
  // Code blocks: ```language ... ```
  const parts = text.split(/(```[\s\S]*?```)/g);
  
  return parts.map((part, index) => {
    if (part.startsWith('```')) {
      const match = part.match(/```(\w+)?\n([\s\S]*?)```/);
      if (match) {
        const lang = match[1] || 'code';
        const code = match[2];
        return (
          <div key={index} className="my-3 rounded-lg overflow-hidden border border-slate-700/50 dark:border-slate-700 shadow-md">
            <div className="bg-slate-800 dark:bg-slate-900 text-slate-300 px-3 py-1.5 text-xs font-mono border-b border-slate-700/50 flex justify-between">
              <span>{lang}</span>
            </div>
            <pre className="p-4 bg-slate-900 dark:bg-[#0d1117] text-slate-100 overflow-x-auto text-sm font-mono leading-relaxed">
              <code>{code}</code>
            </pre>
          </div>
        );
      }
      return part;
    }
    
    // Bold, inline code, links
    let formattedText = part;
    // VERY simple formatting for demonstration
    // It's safer to use react-markdown for a real app, but this creates the visual effect
    const lines = formattedText.split('\n');
    return (
      <div key={index} className="space-y-2">
        {lines.map((line, i) => {
          if (!line.trim()) return <div key={i} className="h-1"></div>;
          if (line.startsWith('- ') || line.startsWith('* ')) {
            return <li key={i} className="ml-6 list-disc my-1">{line.substring(2)}</li>;
          }
          if (line.match(/^\d+\.\s/)) {
            const matchList = line.match(/^(\d+\.\s)(.*)/);
            return <li key={i} className="ml-6 list-decimal my-1 font-medium text-purple-700 dark:text-purple-400"><span className="font-normal text-slate-800 dark:text-slate-200">{matchList ? matchList[2] : line}</span></li>;
          }
          if (line.startsWith('# ')) {
            return <h1 key={i} className="text-xl font-bold mt-5 mb-3 text-purple-800 dark:text-purple-300">{line.substring(2)}</h1>;
          }
          if (line.startsWith('## ')) {
            return <h2 key={i} className="text-lg font-bold mt-4 mb-2 text-purple-700 dark:text-purple-400">{line.substring(3)}</h2>;
          }
          if (line.startsWith('### ')) {
            return <h3 key={i} className="text-md font-bold mt-3 mb-1 text-slate-800 dark:text-slate-200">{line.substring(4)}</h3>;
          }
          if (line.startsWith('> ')) {
            return <blockquote key={i} className="border-l-4 border-purple-500 pl-4 py-1 my-2 bg-slate-100 dark:bg-slate-800/50 italic">{line.substring(2)}</blockquote>;
          }
          
          // Basic strong/code replacement
          const elements = [];
          let currentStr = line;
          let match;
          const rgx = /(\*\*.*?\*\*|`.*?`|\[.*?\]\(.*?\))/;
          
          while ((match = currentStr.match(rgx)) !== null) {
            const index = match.index;
            if (index > 0) elements.push(<span key={currentStr.length + "t"}>{currentStr.substring(0, index)}</span>);
            
            const matched = match[0];
            if (matched.startsWith('**')) {
              elements.push(<strong key={currentStr.length} className="font-semibold text-purple-600 dark:text-purple-300">{matched.substring(2, matched.length - 2)}</strong>);
            } else if (matched.startsWith('`')) {
              elements.push(<code key={currentStr.length} className="px-1.5 py-0.5 bg-slate-200 dark:bg-slate-800 text-purple-600 dark:text-purple-300 rounded font-mono text-sm">{matched.substring(1, matched.length - 1)}</code>);
            } else if (matched.startsWith('[')) {
              const linkMatch = matched.match(/\[(.*?)\]\((.*?)\)/);
              if (linkMatch) {
                elements.push(<a key={currentStr.length} href={linkMatch[2]} className="text-blue-500 hover:underline inline-flex items-center gap-1 font-medium"><Icons.Link /> {linkMatch[1]}</a>);
              }
            }
            currentStr = currentStr.substring(index + matched.length);
          }
          if (currentStr) elements.push(<span key={currentStr.length + "e"}>{currentStr}</span>);
          
          return <p key={i}>{elements.length > 0 ? elements : line}</p>;
        })}
      </div>
    );
  });
};

export default function ChatPage() {
  const [projectId, setProjectId] = useState('PFAProject');
  const [serviceContext, setServiceContext] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [theme, setTheme] = useState('dark');
  const [initialized, setInitialized] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const searchParams = new URLSearchParams(window.location.search);
      const pid = searchParams.get('project_id');
      const svc = searchParams.get('service');
      if (pid) setProjectId(pid);
      if (svc) setServiceContext(svc);
      setInitialized(true);
    }
  }, []);

  // Suggestions automatiques
  const suggestions = [
    "Explique l'architecture de ce projet",
    "Trouve les dépendances principales",
    "Résume le contenu de rollo_docs",
    "Quelles sont les entités du domaine ?"
  ];

  // Dynamic File Tree
  const [fileTree, setFileTree] = useState([]);

  useEffect(() => {
    // Fetch file tree from API
    const fetchTree = async () => {
      try {
        const res = await fetch('/api/tree');
        if (res.ok) {
          const data = await res.json();
          setFileTree(data);
        }
      } catch (err) {
        console.error("Failed to load file tree", err);
      }
    };
    fetchTree();
  }, []);

  useEffect(() => {
    if (!initialized) return;

    // Check initial theme
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      setTheme('dark');
      document.documentElement.classList.add('dark');
    } else {
      setTheme('light');
      document.documentElement.classList.remove('dark');
    }

    const contextMsg = serviceContext 
      ? `👋 Bienvenue ! Je suis l'assistant IA spécialisé dans le composant **${serviceContext}** du projet **${projectId}**.\n\nJe vais répondre en me basant en priorité sur la documentation de ce service.`
      : `👋 Bienvenue sur l'assistant **Rollo** du projet **${projectId}**.\n\nJe suis connecté à l'ensemble du projet, y compris aux dossiers \`rollo_docs\` et aux fichiers sources. Posez-moi une question ou utilisez une suggestion ci-dessous.`;

    setMessages([
      { 
        role: 'assistant', 
        content: contextMsg,
        sources: serviceContext ? [`rollo_docs/${serviceContext}/README.md`] : ['rollo.md', 'rollo_docs/README.md']
      }
    ]);
  }, [projectId, serviceContext, initialized]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const toggleTheme = () => {
    const newTheme = theme === 'dark' ? 'light' : 'dark';
    setTheme(newTheme);
    if (newTheme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  };

  const handleSuggestion = (text) => {
    sendMessage(null, text);
  };

  const sendMessage = async (e, customText = null) => {
    if (e) e.preventDefault();
    const userMessage = customText || input;
    if (!userMessage.trim() || isLoading) return;

    setInput('');
    setMessages(prev => [...prev, { role: 'user', content: userMessage }]);
    setIsLoading(true);

    try {
      // Pour l'interface, si l'API n'est pas encore prête, on simule une réponse intelligente
      // Remplacez cela par un vrai fetch vers /api/chat
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: userMessage, projectId, service: serviceContext })
      });

      let data;
      if (res.ok) {
        data = await res.json();
      } else {
        const errData = await res.json().catch(() => null);
        throw new Error(errData?.error || 'Erreur lors de la communication avec le LLM');
      }

      setMessages(prev => [...prev, { 
        role: 'assistant', 
        content: data.reply,
        sources: data.sources || ['Code source', 'Documentation']
      }]);
    } catch (error) {
      setMessages(prev => [...prev, { role: 'assistant', content: `❌ Erreur : ${error.message}` }]);
    } finally {
      setIsLoading(false);
    }
  };

  const [searchQuery, setSearchQuery] = useState('');

  const filterTree = (nodes, query) => {
    if (!query) return nodes;
    const lowerQuery = query.toLowerCase();
    
    return nodes.map(node => {
      if (node.type === 'file') {
        if (node.name.toLowerCase().includes(lowerQuery)) return node;
        return null;
      }
      
      // Folder
      if (node.name.toLowerCase().includes(lowerQuery)) return { ...node, open: true };
      
      const filteredChildren = filterTree(node.children || [], query);
      const validChildren = filteredChildren.filter(Boolean);
      
      if (validChildren.length > 0) {
        return { ...node, children: validChildren, open: true };
      }
      return null;
    }).filter(Boolean);
  };

  // Limiter le contenu à rollo.md, rollo_detailled.md et rollo_docs
  const rolloBaseTree = fileTree
    .filter(node => ['rollo.md', 'rollo_detailled.md', 'rollo_docs'].includes(node.name))
    .map(node => node.name === 'rollo_docs' ? { ...node, open: true } : node);

  const filteredTree = filterTree(rolloBaseTree, searchQuery);

  const Folder = ({ node }) => {
    const [isOpen, setIsOpen] = useState(node.open || false);
    
    // Auto-open if searching
    useEffect(() => {
      if (searchQuery) setIsOpen(true);
    }, [searchQuery]);

    return (
      <div className="pl-3 py-1">
        <div 
          onClick={() => setIsOpen(!isOpen)}
          className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300 hover:text-purple-600 dark:hover:text-purple-400 cursor-pointer transition-colors select-none"
        >
          <Icons.Folder />
          <span className="truncate">{node.name}</span>
        </div>
        {node.children && isOpen && (
          <div className="border-l border-slate-200 dark:border-slate-700 ml-2 mt-1">
            {renderTree(node.children)}
          </div>
        )}
      </div>
    );
  };

  const renderTree = (nodes) => {
    if (!nodes) return null;
    return nodes.map((node, i) => {
      if (node.type === 'folder') {
        return <Folder key={i} node={node} />;
      }
      return (
        <div key={i} className="pl-3 py-1">
          <div 
            onClick={() => handleSuggestion(`Explique le fichier ${node.name}`)}
            className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300 hover:text-purple-600 dark:hover:text-purple-400 cursor-pointer transition-colors select-none"
          >
            <Icons.File />
            <span className="truncate">{node.name}</span>
          </div>
        </div>
      );
    });
  };

  return (
    <div className="h-screen flex bg-slate-50 dark:bg-[#0b1120] text-slate-900 dark:text-slate-100 font-sans overflow-hidden transition-colors duration-300">
      
      {/* Sidebar - Explorateur Latéral */}
      <div className="w-72 border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0f172a] hidden md:flex flex-col shadow-sm z-10">
        <div className="p-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-md bg-gradient-to-br from-purple-500 to-indigo-600 flex items-center justify-center text-white">
              <Icons.Bot />
            </div>
            <span className="font-bold text-lg bg-clip-text text-transparent bg-gradient-to-r from-purple-600 to-indigo-600 dark:from-purple-400 dark:to-indigo-400">
              Rollo
            </span>
          </div>
        </div>
        
        <div className="p-3 border-b border-slate-200 dark:border-slate-800">
          <div className="relative">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
              <Icons.Search />
            </div>
            <input 
              type="text" 
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Rechercher des fichiers..." 
              className="w-full pl-9 pr-3 py-2 bg-slate-100 dark:bg-slate-800/50 border-none rounded-lg text-sm focus:ring-2 focus:ring-purple-500 outline-none text-slate-700 dark:text-slate-200 placeholder-slate-400"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-2">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2 ml-2 mt-2">
            Projet: {projectId}
          </div>
          {renderTree(filteredTree)}
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col relative h-full bg-slate-50 dark:bg-transparent">
        {/* Header */}
        <header className="h-16 border-b border-slate-200 dark:border-slate-800 bg-white/80 dark:bg-[#0f172a]/80 backdrop-blur-md flex items-center justify-between px-6 sticky top-0 z-20">
          <div className="flex items-center gap-3">
            <h1 className="font-semibold text-lg">Chat Contextuel</h1>
            {serviceContext ? (
              <div className="flex items-center gap-3">
                <span className="flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 text-xs font-medium border border-purple-200 dark:border-purple-800/50">
                  <span className="w-1.5 h-1.5 rounded-full bg-purple-500 animate-pulse"></span>
                  👉 Contexte : {serviceContext}
                </span>
                <button 
                  onClick={() => {
                    window.location.href = `/?project_id=${projectId}`;
                  }}
                  className="text-xs bg-slate-200 hover:bg-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 px-3 py-1.5 rounded-md font-medium transition-colors shadow-sm"
                >
                  Basculer vers tout le projet
                </button>
              </div>
            ) : (
              <span className="flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 text-xs font-medium border border-green-200 dark:border-green-800/50">
                <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>
                Connecté au Projet Global
              </span>
            )}
          </div>
          <button 
            onClick={toggleTheme}
            className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400 transition-colors"
          >
            {theme === 'dark' ? <Icons.Sun /> : <Icons.Moon />}
          </button>
        </header>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 md:p-8 space-y-8 scroll-smooth">
          <div className="max-w-4xl mx-auto space-y-8">
            {messages.map((msg, idx) => (
              <div key={idx} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div className={`flex max-w-[85%] gap-4 ${msg.role === 'user' ? 'flex-row-reverse' : 'flex-row'}`}>
                  
                  {/* Avatar */}
                  <div className={`w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center shadow-sm mt-1 ${
                    msg.role === 'user' 
                      ? 'bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-300' 
                      : 'bg-gradient-to-br from-purple-500 to-indigo-600 text-white'
                  }`}>
                    {msg.role === 'user' ? <Icons.User /> : <Icons.Bot />}
                  </div>

                  {/* Bubble */}
                  <div className={`flex flex-col gap-2 ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
                    <div className={`rounded-2xl px-5 py-4 shadow-sm text-sm md:text-base leading-relaxed ${
                      msg.role === 'user' 
                        ? 'bg-purple-600 text-white rounded-tr-sm' 
                        : 'bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700/60 rounded-tl-sm text-slate-800 dark:text-slate-200'
                    }`}>
                      {msg.role === 'user' ? msg.content : renderMarkdown(msg.content)}
                    </div>
                    
                    {/* Sources Indicator */}
                    {msg.role === 'assistant' && msg.sources && msg.sources.length > 0 && (
                      <div className="flex flex-wrap gap-2 mt-1">
                        {msg.sources.map((source, sIdx) => (
                          <div key={sIdx} className="flex items-center gap-1 text-[11px] px-2 py-1 bg-slate-200 dark:bg-slate-800/80 text-slate-600 dark:text-slate-400 rounded-md border border-slate-300 dark:border-slate-700/50">
                            <Icons.Terminal /> {source}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                </div>
              </div>
            ))}
            
            {isLoading && (
              <div className="flex justify-start">
                <div className="flex gap-4 max-w-[85%]">
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-purple-500 to-indigo-600 text-white flex items-center justify-center shadow-sm mt-1">
                     <Icons.Bot />
                  </div>
                  <div className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700/60 rounded-2xl rounded-tl-sm px-5 py-4 shadow-sm flex items-center gap-1.5 h-12">
                    <span className="w-2 h-2 rounded-full bg-purple-500 animate-bounce"></span>
                    <span className="w-2 h-2 rounded-full bg-purple-500 animate-bounce" style={{ animationDelay: '150ms' }}></span>
                    <span className="w-2 h-2 rounded-full bg-purple-500 animate-bounce" style={{ animationDelay: '300ms' }}></span>
                  </div>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        </div>

        {/* Input Area */}
        <div className="p-4 md:px-8 md:pb-8 bg-gradient-to-t from-slate-50 via-slate-50 dark:from-[#0b1120] dark:via-[#0b1120] to-transparent sticky bottom-0">
          <div className="max-w-4xl mx-auto flex flex-col gap-3">
            
            {/* Suggestions */}
            {messages.length < 3 && !isLoading && (
              <div className="flex flex-wrap gap-2 mb-2">
                {suggestions.map((sug, idx) => (
                  <button 
                    key={idx}
                    onClick={() => handleSuggestion(sug)}
                    className="text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 hover:border-purple-400 dark:hover:border-purple-500 text-slate-600 dark:text-slate-300 px-3 py-1.5 rounded-full transition-colors flex items-center gap-1 shadow-sm"
                  >
                    <Icons.Sparkles /> {sug}
                  </button>
                ))}
              </div>
            )}

            <form onSubmit={(e) => sendMessage(e)} className="relative group shadow-lg rounded-2xl overflow-hidden bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 focus-within:border-purple-500 dark:focus-within:border-purple-500 transition-colors">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="Interrogez le projet, demandez une explication de fichier..."
                className="w-full bg-transparent px-6 py-4 pr-16 focus:outline-none text-slate-800 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 text-[15px]"
              />
              <button 
                type="submit" 
                disabled={isLoading || !input.trim()}
                className="absolute right-2 top-2 bottom-2 bg-purple-600 hover:bg-purple-700 disabled:bg-slate-300 dark:disabled:bg-slate-700 text-white w-10 flex items-center justify-center rounded-xl transition-colors disabled:cursor-not-allowed"
              >
                <Icons.Send />
              </button>
            </form>
            <div className="text-center text-[11px] text-slate-500 dark:text-slate-400">
              L'assistant utilise les fichiers locaux et <b>rollo_docs</b> pour générer ses réponses.
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}
