import fs from 'fs';
import path from 'path';
import dns from 'dns';

// Force l'utilisation d'IPv4 en premier pour résoudre les bugs de DNS/IPv6 de Node.js sur Windows
dns.setDefaultResultOrder('ipv4first');

export async function POST(req) {
  try {
    const { message, projectId, service } = await req.json();
    
    // Read documentation and context selectively to stay within Groq TPM limits (6000 tokens)
    let docContext = "";
    let sources = [];
    const rootPath = path.join(process.cwd(), '..');
    
    try {
      // 1. Always include the high-level summary (rollo.md)
      const rolloPath = path.join(rootPath, 'rollo.md');
      if (fs.existsSync(rolloPath)) {
        docContext += "--- PROJECT SUMMARY (rollo.md) ---\n" + fs.readFileSync(rolloPath, 'utf8') + "\n";
        sources.push("rollo.md");
      }
      
      // 2. Smart Context Loading: scan the message for component names to load relevant docs
      const rolloDocsPath = path.join(rootPath, 'rollo_docs');
      let loadedComponents = 0;
      
      if (fs.existsSync(rolloDocsPath)) {
        const components = fs.readdirSync(rolloDocsPath).filter(f => fs.lstatSync(path.join(rolloDocsPath, f)).isDirectory());
        
        // Always include the specific component if a service is provided
        const targetComponents = new Set();
        if (service) {
          targetComponents.add(service);
        } else if (projectId && projectId !== 'root') {
          // Fallback if projectId was misused as a service name in older versions
          targetComponents.add(projectId);
        }
        
        // Add components mentioned in the message
        const messageLower = message.toLowerCase();
        for (const comp of components) {
          if (messageLower.includes(comp.toLowerCase())) {
            targetComponents.add(comp);
          }
        }
        
        // Load documentation for targeted components
        for (const comp of targetComponents) {
          const componentPath = path.join(rolloDocsPath, comp);
          if (fs.existsSync(componentPath)) {
            const files = fs.readdirSync(componentPath).filter(f => f.endsWith('.md'));
            docContext += `\n--- COMPONENT CONTEXT (${comp}) ---\n`;
            for (const file of files) {
              const content = fs.readFileSync(path.join(componentPath, file), 'utf8');
              docContext += `\n[File: ${file}]\n${content}\n`;
              sources.push(`rollo_docs/${comp}/${file}`);
            }
            loadedComponents++;
          }
        }
      }

      // 3. If no specific components were targeted, include a chunk of the detailed architecture
      if (loadedComponents === 0) {
        const detailedPath = path.join(rootPath, 'rollo_detailled.md');
        if (fs.existsSync(detailedPath)) {
          const detailedContent = fs.readFileSync(detailedPath, 'utf8');
          // Limit to first ~15000 chars to avoid breaking the TPM limit
          docContext += "\n--- DETAILED ARCHITECTURE (rollo_detailled.md) ---\n" + detailedContent.substring(0, 15000) + "\n...\n(Tronqué pour respecter la limite de tokens)";
          sources.push("rollo_detailled.md");
        }
      }
      
      if (!docContext.trim()) {
        docContext = "La documentation détaillée n'est pas encore générée pour ce projet.";
      }
    } catch (e) {
      console.error("Error reading docs:", e);
      docContext = "Erreur de lecture de la documentation.";
    }

    const apiKey = process.env.GROQ_API_KEY;
    if (!apiKey) {
      return new Response(JSON.stringify({ error: "Veuillez configurer votre GROQ_API_KEY dans le fichier .env.local du frontend." }), { status: 500 });
    }

    let systemPrompt;
    if (service) {
      systemPrompt = `Tu es un assistant technique expert et architecte logiciel spécialisé dans le composant "${service}".
Ton rôle est d'aider l'utilisateur à comprendre ce service spécifique dans le projet "${projectId || 'mcp-github-V4'}".
Réponds uniquement à partir de ce service sauf si nécessaire.

CONSIGNES DE RÉPONSE :
1. Sois très précis et technique.
2. Structure ta réponse avec des titres (###), des listes à puces et du texte en gras pour faire ressortir les mots-clés.
3. Utilise des blocs de code markdown (\`\`\`langage) pour illustrer tes explications.
4. Si tu expliques un processus, fais-le étape par étape.
5. Indique toujours si une information est manquante dans le contexte fourni.

Voici la documentation contextuelle :

<documentation>
${docContext}
</documentation>

Réponds à la question de l'utilisateur de manière structurée et pédagogique en français.`;
    } else {
      systemPrompt = `Tu es un développeur senior et architecte logiciel. Ton rôle est d'aider l'utilisateur à comprendre le code, l'architecture et les concepts de son projet "${projectId || 'mcp-github-V4'}".

CONSIGNES DE RÉPONSE :
1. Structure toujours tes réponses de manière aérée (utilise des titres en markdown ## ou ###).
2. Utilise des listes à puces pour énumérer les points importants.
3. Mets en évidence le code, les noms de fichiers et de variables avec des backticks (\`code\`) ou des blocs de code complets.
4. Sois direct, pédagogique et pragmatique.
5. Base-toi EXCLUSIVEMENT sur la documentation fournie. Si la réponse ne s'y trouve pas, dis-le clairement.

Voici la documentation contextuelle du projet :

<documentation>
${docContext}
</documentation>

Réponds à la question de l'utilisateur de manière précise, concise, en français. Utilise systématiquement du Markdown pour formater ta réponse.`;
    }

    const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: "llama-3.3-70b-versatile",
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: message }
        ],
        temperature: 0.2 // Slightly higher to allow better formatting but still factual
      })
    });

    const data = await response.json();
    
    if (!response.ok) {
      // Check for rate limit specifically
      if (response.status === 429 || data.error?.code === "rate_limit_exceeded") {
        return new Response(JSON.stringify({ 
          error: "Limite de tokens Groq atteinte (6000 TPM). Réessayez dans quelques secondes ou simplifiez votre demande." 
        }), { status: 429 });
      }
      return new Response(JSON.stringify({ error: data.error?.message || "Erreur lors de l'appel au LLM" }), { status: 500 });
    }

    return new Response(JSON.stringify({ 
      reply: data.choices[0].message.content,
      sources: [...new Set(sources)] // Remove duplicates
    }), { status: 200 });

  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }
}
