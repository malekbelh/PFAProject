import fs from 'fs';
import path from 'path';

function buildTree(dirPath, name, isRoot = false) {
  const node = { type: 'folder', name, children: [], open: false };
  if (isRoot) node.open = true; // root is open by default

  try {
    const items = fs.readdirSync(dirPath, { withFileTypes: true });

    // Sort directories first, then files
    const sortedItems = items.sort((a, b) => {
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      return a.name.localeCompare(b.name);
    });

    for (const item of sortedItems) {
      if (item.name === 'node_modules' || item.name === '.git' || item.name === 'target' || item.name === '.next' || item.name === 'chat-frontend' || item.name === '.vscode' || item.name === '.mvn') continue;

      const fullPath = path.join(dirPath, item.name);

      if (item.isDirectory()) {
        const childNode = buildTree(fullPath, item.name);
        if (childNode.children.length > 0 || ['src', 'rollo_docs'].includes(item.name)) {
          // Open src and rollo_docs by default
          if (['src', 'rollo_docs'].includes(item.name)) childNode.open = true;
          node.children.push(childNode);
        }
      } else {
        // ignore some files if needed, but we'll include most
        if (!item.name.endsWith('.log') && item.name !== '.gitignore' && item.name !== '.gitattributes' && !item.name.startsWith('mvnw')) {
          node.children.push({ type: 'file', name: item.name });
        }
      }
    }
  } catch (error) {
    console.error("Error building tree:", error);
  }

  return node;
}

export async function GET(request) {
  try {
    const url = new URL(request.url);
    const projectId = url.searchParams.get('project_id') ?? '';
    const rootPath = path.join(process.cwd(), '..');
    const tree = buildTree(rootPath, projectId, true);
    // We want the children of the root to be the top-level array, not the root itself
    return new Response(JSON.stringify(tree.children), { status: 200 });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }
}

