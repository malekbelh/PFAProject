import fs from 'fs';
import path from 'path';

// Map file extensions to Monaco language identifiers
function getLanguage(filename) {
  const ext = filename.split('.').pop().toLowerCase();
  const map = {
    js: 'javascript', jsx: 'javascript', ts: 'typescript', tsx: 'typescript',
    java: 'java', py: 'python', rb: 'ruby', go: 'go', rs: 'rust',
    cs: 'csharp', cpp: 'cpp', c: 'c', h: 'c',
    html: 'html', css: 'css', scss: 'scss', less: 'less',
    json: 'json', xml: 'xml', yaml: 'yaml', yml: 'yaml',
    md: 'markdown', sh: 'shell', bash: 'shell',
    sql: 'sql', graphql: 'graphql',
    toml: 'ini', properties: 'ini', env: 'ini',
  };
  return map[ext] || 'plaintext';
}

export async function GET(req) {
  try {
    const { searchParams } = new URL(req.url);
    const filePath = searchParams.get('path');

    if (!filePath) {
      return new Response(JSON.stringify({ error: 'Missing path parameter' }), { status: 400 });
    }

    // Security: resolve against project root and ensure no path traversal
    const rootPath = path.resolve(path.join(process.cwd(), '..'));
    const resolvedPath = path.resolve(path.join(rootPath, filePath));

    if (!resolvedPath.startsWith(rootPath)) {
      return new Response(JSON.stringify({ error: 'Access denied' }), { status: 403 });
    }

    if (!fs.existsSync(resolvedPath)) {
      return new Response(JSON.stringify({ error: 'File not found' }), { status: 404 });
    }

    const stat = fs.statSync(resolvedPath);
    if (stat.isDirectory()) {
      return new Response(JSON.stringify({ error: 'Path is a directory' }), { status: 400 });
    }

    // Limit file size to 500KB to avoid crashing the editor
    if (stat.size > 512000) {
      return new Response(JSON.stringify({ error: 'File too large (> 500KB)' }), { status: 413 });
    }

    const content = fs.readFileSync(resolvedPath, 'utf8');
    const filename = path.basename(resolvedPath);

    return new Response(
      JSON.stringify({ content, language: getLanguage(filename), filename }),
      { status: 200 }
    );
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }
}
