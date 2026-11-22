import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { EmptyState, ErrorState, Skeleton } from '../../design/primitives';

export interface RepoRef {
  owner: string;
  name: string;
  description: string;
  language: string;
  stars: number;
  forks: number;
  /** Display name for the repository creator (Discussion badges). */
  ownerName?: string;
  /** True when the signed-in student owns this repository. */
  viewerIsOwner?: boolean;
}

const RV_CSS = `
.rv-head{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:4px}
.rv-head .rv-name{font-size:1.25rem}
.rv-head .rv-owner{color:var(--text-secondary)}
.rv-pill{border:1px solid var(--border);border-radius:999px;padding:1px 9px;font-size:.76rem;font-weight:600}
.rv-counts{margin-left:auto;display:flex;gap:8px}
.rv-count{border:1px solid var(--border);border-radius:999px;padding:3px 11px;font-size:.85rem;background:var(--surface);color:inherit;font:inherit}
button.rv-count{cursor:pointer}
button.rv-count[aria-pressed="true"]{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
.rv-tabs{display:flex;gap:4px;border-bottom:1px solid var(--border);margin:14px 0 0}
.rv-tabs button{background:none;border:none;border-bottom:2px solid transparent;padding:8px 12px;cursor:pointer;color:inherit;font:inherit}
.rv-tabs button[aria-selected="true"]{border-bottom-color:var(--text-primary);font-weight:600}
.rv-layout{display:grid;grid-template-columns:230px minmax(0,1fr) 250px;gap:20px;align-items:start;margin-top:14px}
.rv-tree{border:1px solid var(--border);border-radius:var(--radius-md);padding:8px}
.rv-tree-head{display:flex;justify-content:space-between;align-items:center;padding:2px 4px 8px}
.rv-tree-head strong{font-size:.88rem}
.rv-tree-toggle{background:none;border:1px solid var(--border);border-radius:var(--radius-sm);cursor:pointer;color:inherit;font:inherit;font-size:.78rem;padding:2px 8px}
.rv-node{display:flex;gap:6px;align-items:center;width:100%;background:none;border:none;padding:4px 6px;cursor:pointer;color:inherit;font:inherit;font-size:.88rem;text-align:left;border-radius:var(--radius-sm)}
.rv-node:hover{background:var(--surface-muted)}
.rv-node[aria-current="true"]{background:var(--surface-muted);font-weight:600}
.rv-node .rv-caret{color:var(--text-secondary);font-size:.7rem;width:10px;flex:none}
.rv-branchbar{display:flex;justify-content:space-between;gap:12px;align-items:center;margin:2px 0 10px;flex-wrap:wrap}
.rv-branch{border:1px solid var(--border);border-radius:999px;padding:4px 12px;font-size:.88rem;background:var(--surface-muted)}
.rv-commit{color:var(--text-secondary);font-size:.88rem}
.rv-crumb{display:flex;gap:6px;align-items:center;margin:0 0 10px;font-size:.95rem;flex-wrap:wrap}
.rv-crumb button{background:none;border:none;color:inherit;cursor:pointer;font:inherit;padding:0}
.rv-crumb button:hover{text-decoration:underline}
.rv-crumb .rv-sep{color:var(--text-secondary)}
.rv-table{border:1px solid var(--border);border-radius:var(--radius-md);overflow:hidden}
.rv-row{display:grid;grid-template-columns:minmax(140px,1fr) 1.4fr auto;gap:12px;padding:9px 14px;border-bottom:1px solid var(--border);align-items:center;width:100%;background:none;border-left:none;border-right:none;border-top:none;text-align:left;color:inherit;font:inherit;cursor:pointer}
.rv-row:last-child{border-bottom:none}
.rv-row:hover{background:var(--surface-muted)}
.rv-row .rv-fname{display:flex;gap:9px;align-items:center;font-weight:550}
.rv-row .rv-msg{color:var(--text-secondary);font-size:.88rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.rv-row .rv-when{color:var(--text-secondary);font-size:.85rem}
.rv-filebar{display:flex;gap:12px;align-items:center;border:1px solid var(--border);border-bottom:none;border-radius:var(--radius-md) var(--radius-md) 0 0;padding:8px 12px;background:var(--surface-muted);flex-wrap:wrap}
.rv-filebar .rv-fpath{font-weight:600;font-size:.9rem}
.rv-filebar .rv-fmeta{color:var(--text-secondary);font-size:.83rem}
.rv-filebar .rv-factions{margin-left:auto;display:flex;gap:8px}
.rv-codebtn{border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface);color:inherit;font:inherit;font-size:.82rem;padding:3px 10px;cursor:pointer}
.rv-codebtn[aria-pressed="true"]{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
.rv-code{border:1px solid var(--border);border-radius:0 0 var(--radius-md) var(--radius-md);overflow:auto;background:var(--surface)}
.rv-code table{border-collapse:collapse;width:100%}
.rv-code td{padding:0 12px 0 0;vertical-align:top}
.rv-code .rv-ln{color:var(--text-secondary);text-align:right;user-select:none;width:1%;white-space:nowrap;padding-left:14px;font-size:.8rem;line-height:1.6}
.rv-code .rv-lc{white-space:pre;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.83rem;line-height:1.6}
.rv-code-raw{margin:0;padding:14px 16px;white-space:pre;overflow:auto;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.83rem;line-height:1.6}
.tok-k{font-weight:650}
.tok-s{color:#7a5c2e}
.tok-c{color:var(--text-secondary);font-style:italic}
.tok-n{color:#5c6650}
.rv-readme{border:1px solid var(--border);border-radius:var(--radius-md);padding:20px 22px;margin-top:18px}
.rv-readme h1{font-size:1.35rem;margin:0 0 10px}
.rv-readme h2{font-size:1.02rem;margin:18px 0 6px}
.rv-readme p{color:var(--text-secondary);margin:0 0 8px}
.rv-readme ul{margin:6px 0;padding-left:20px;color:var(--text-secondary)}
.rv-readme pre{background:var(--surface-muted);border:1px solid var(--border);border-radius:var(--radius-sm);padding:12px;overflow:auto;font-size:.84rem}
.rv-about{border:1px solid var(--border);border-radius:var(--radius-md);padding:16px}
.rv-about h2{font-size:.95rem;margin:0 0 8px}
.rv-about p{color:var(--text-secondary);font-size:.9rem;margin:0 0 10px}
.rv-topics{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px}
.rv-meta{color:var(--text-secondary);font-size:.88rem;display:flex;flex-direction:column;gap:5px}
.rv-dot{width:9px;height:9px;border-radius:50%;background:var(--text-secondary);display:inline-block;margin-right:5px}
.rv-langbar{display:flex;height:8px;border-radius:99px;overflow:hidden;border:1px solid var(--border);margin:6px 0 4px}
.rv-filetag{font-family:ui-monospace,Menlo,monospace;background:var(--surface-muted);border:1px solid var(--border);border-radius:6px;padding:0 6px;cursor:pointer;color:inherit;font-size:.86em}
.rv-filetag:hover{border-color:var(--text-secondary)}
.rv-creator{background:var(--primary);color:var(--on-primary);border-radius:999px;padding:1px 8px;font-size:.68rem;font-weight:700;letter-spacing:.02em}
.rv-disc-comment{display:flex;gap:10px;padding:12px 0;border-bottom:1px solid var(--border)}
.rv-disc-avatar{width:32px;height:32px;border-radius:50%;background:var(--primary);color:var(--on-primary);display:inline-flex;align-items:center;justify-content:center;font-size:.72rem;font-weight:700;flex:none}
.rv-disc-main{flex:1;min-width:0}
.rv-disc-head{margin:0;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.rv-disc-body{margin:3px 0 0}
.rv-disc-actions{display:flex;gap:14px;margin-top:5px}
.rv-disc-actions button{background:none;border:none;padding:0;cursor:pointer;color:var(--text-secondary);font:inherit;font-size:.78rem;font-weight:600}
.rv-disc-replies{border-left:2px solid var(--border);margin:10px 0 2px 15px;padding-left:14px}
.rv-disc-composer{position:relative;margin:14px 0 4px}
.rv-disc-composer textarea{width:100%;border:1px solid var(--border);border-radius:var(--radius-md);background:var(--surface);color:inherit;font:inherit;padding:10px 12px;resize:vertical}
.rv-mention-list{position:absolute;left:0;right:auto;top:calc(100% - 38px);min-width:280px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-md);box-shadow:0 10px 28px rgb(20 20 15 / .2);z-index:30;padding:4px}
.rv-mention-list button{display:flex;gap:8px;align-items:center;width:100%;background:none;border:none;padding:7px 9px;cursor:pointer;color:inherit;font-family:ui-monospace,Menlo,monospace;font-size:.83rem;text-align:left;border-radius:var(--radius-sm)}
.rv-mention-list button:hover{background:var(--surface-muted)}
.rv-issue{display:flex;gap:10px;align-items:baseline;padding:10px 4px;border-bottom:1px solid var(--border)}
.rv-issue:last-child{border-bottom:none}
.rv-commit-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:14px;padding:11px 4px;border-bottom:1px solid var(--border);align-items:baseline}
.rv-commit-row:last-child{border-bottom:none}
.rv-hash{font-family:ui-monospace,Menlo,monospace;font-size:.8rem;border:1px solid var(--border);border-radius:var(--radius-sm);padding:1px 7px;background:var(--surface-muted)}
@media (max-width:1100px){.rv-layout{grid-template-columns:210px minmax(0,1fr)}.rv-about{grid-column:1/-1}}
@media (max-width:760px){.rv-layout{grid-template-columns:minmax(0,1fr)}.rv-row{grid-template-columns:minmax(120px,1fr) auto}.rv-row .rv-msg{display:none}}
`;

/* ------------------------------------------------------------------ */
/* Fixture repository model. The file tree, file contents and commit  */
/* history are presentation fixtures keyed to the repository until    */
/* the real GitHub API integration lands; the viewer is read-only.    */
/* ------------------------------------------------------------------ */

interface FileRow { name: string; dir?: boolean; msg: string; when: string }
const TREES: Record<string, FileRow[]> = {
  '': [
    { name: 'src', dir: true, msg: 'Refactor app shell and routes', when: '3 hours ago' },
    { name: 'public', dir: true, msg: 'Add app icons and manifest', when: '2 days ago' },
    { name: 'package.json', msg: 'Bump dependencies to latest minor versions', when: '5 days ago' },
    { name: 'tsconfig.json', msg: 'Enable strict mode checks', when: '2 weeks ago' },
    { name: '.gitignore', msg: 'Ignore build output and local env files', when: '3 weeks ago' },
    { name: 'README.md', msg: 'Document setup, features and usage', when: '2 hours ago' },
  ],
  src: [
    { name: 'components', dir: true, msg: 'Extract shared header component', when: '3 hours ago' },
    { name: 'pages', dir: true, msg: 'Add the live map tracking page', when: '2 hours ago' },
    { name: 'App.tsx', msg: 'Wire the live map view into the shell', when: '2 hours ago' },
    { name: 'main.tsx', msg: 'Bootstrap the app with providers', when: '2 weeks ago' },
    { name: 'styles.css', msg: 'Warm paper theme tokens', when: '4 days ago' },
  ],
  'src/components': [
    { name: 'Header.tsx', msg: 'Add the top navigation bar', when: '3 hours ago' },
    { name: 'RepoCard.tsx', msg: 'Repository summary card', when: '1 day ago' },
  ],
  'src/pages': [
    { name: 'MapTracker.tsx', msg: 'Live bus positions with route sharing', when: '2 hours ago' },
    { name: 'Home.tsx', msg: 'Landing summary and quick links', when: '1 day ago' },
  ],
  public: [
    { name: 'favicon.svg', msg: 'Add the app icon', when: '2 days ago' },
    { name: 'robots.txt', msg: 'Allow public indexing', when: '3 weeks ago' },
  ],
};

const COMMITS = [
  { hash: 'a3f9c21', message: 'feat: add live map view and route sharing', when: '2 hours ago' },
  { hash: '8d41b7e', message: 'refactor: extract shared header component', when: '3 hours ago' },
  { hash: 'c02e5aa', message: 'fix: marker flicker when positions update', when: '1 day ago' },
  { hash: '71bd390', message: 'docs: document setup, features and usage', when: '2 days ago' },
  { hash: '5e6f014', message: 'chore: bump dependencies to latest minor versions', when: '5 days ago' },
  { hash: '19ca8d2', message: 'feat: warm paper theme tokens and base styles', when: '1 week ago' },
];

const ISSUES = [
  { n: 12, title: 'Map marker flickers when the bus updates position', label: 'bug' },
  { n: 11, title: 'Add a dark theme for the dashboard', label: 'enhancement' },
  { n: 9, title: 'Document the API client usage in the README', label: 'documentation' },
];
const PRS = [{ n: 14, title: 'feat: route sharing from a phone', author: 'contributor' }];

const ALL_FILES: string[] = Object.entries(TREES)
  .flatMap(([dir, rows]) => rows.filter((r) => !r.dir).map((r) => (dir ? `${dir}/${r.name}` : r.name)))
  .sort();

interface DiscReply { author: string; creator: boolean; time: string; text: string }
interface DiscComment extends DiscReply { replies: DiscReply[] }

function fileContents(repo: RepoRef, path: string): string {
  const snippets: Record<string, string> = {
    'package.json': `{
  "name": "${repo.name}",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.27.0"
  },
  "devDependencies": {
    "typescript": "^5.6.3",
    "vite": "^5.4.9"
  }
}`,
    'src/App.tsx': `import { Routes, Route } from 'react-router-dom';
import { Header } from './components/Header';
import { Home } from './pages/Home';
import { MapTracker } from './pages/MapTracker';

// ${repo.name} — ${repo.description}
export default function App() {
  return (
    <div className="app">
      <Header />
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/map" element={<MapTracker />} />
        </Routes>
      </main>
    </div>
  );
}`,
    'src/main.tsx': `import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);`,
    'src/pages/MapTracker.tsx': `import { useEffect, useState } from 'react';

interface BusPosition {
  id: string;
  route: string;
  lat: number;
  lng: number;
  updatedAt: string;
}

const POLL_MS = 5000;

// Live map page: polls the bus positions endpoint and renders
// one marker per bus on the campus map.
export function MapTracker() {
  const [buses, setBuses] = useState<BusPosition[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const res = await fetch('/api/buses/positions');
        if (!res.ok) throw new Error('positions unavailable');
        const data = (await res.json()) as BusPosition[];
        if (alive) setBuses(data);
      } catch (e) {
        if (alive) setError('Live positions are unavailable right now.');
      }
    };
    load();
    const timer = setInterval(load, POLL_MS);
    return () => { alive = false; clearInterval(timer); };
  }, []);

  if (error) return <p role="alert">{error}</p>;
  return (
    <section>
      <h1>Live bus map</h1>
      <p>{buses.length} buses reporting — updates every {POLL_MS / 1000}s.</p>
      <ul>
        {buses.map((bus) => (
          <li key={bus.id}>
            Route {bus.route} — {bus.lat.toFixed(4)}, {bus.lng.toFixed(4)}
          </li>
        ))}
      </ul>
    </section>
  );
}`,
    'src/pages/Home.tsx': `import { Link } from 'react-router-dom';

export function Home() {
  return (
    <section>
      <h1>${repo.name}</h1>
      <p>${repo.description}</p>
      <p>
        <Link to="/map">Open the live map →</Link>
      </p>
    </section>
  );
}`,
    'src/components/Header.tsx': `import { Link } from 'react-router-dom';

export function Header() {
  return (
    <header className="app-header">
      <Link to="/" className="brand">${repo.name}</Link>
      <nav>
        <Link to="/">Home</Link>
        <Link to="/map">Live map</Link>
      </nav>
    </header>
  );
}`,
    'src/components/RepoCard.tsx': `interface RepoCardProps {
  name: string;
  description: string;
  stars: number;
}

export function RepoCard({ name, description, stars }: RepoCardProps) {
  return (
    <article className="repo-card">
      <h3>{name}</h3>
      <p>{description}</p>
      <span>★ {stars}</span>
    </article>
  );
}`,
    'src/styles.css': `:root {
  --paper: #fdfdfb;
  --ink: #14140f;
  --muted: #6f6a5e;
}

* { box-sizing: border-box; }

body {
  margin: 0;
  background: var(--paper);
  color: var(--ink);
  font-family: system-ui, sans-serif;
}

.app-header {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #e5e2d8;
}`,
    'tsconfig.json': `{
  "compilerOptions": {
    "target": "ES2020",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "strict": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "noEmit": true
  },
  "include": ["src"]
}`,
    '.gitignore': `node_modules
dist
.env.local
*.log`,
    'public/robots.txt': `User-agent: *
Allow: /`,
    'public/favicon.svg': `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
  <rect width="32" height="32" rx="7" fill="#14140f"/>
  <text x="16" y="22" font-size="16" text-anchor="middle" fill="#fdfdfb">U</text>
</svg>`,
    'README.md': `# ${repo.name}

${repo.description}

## Getting started

\`\`\`bash
git clone https://github.com/${repo.owner}/${repo.name}.git
cd ${repo.name}
npm install
npm run dev
\`\`\`

## Usage

Open the local address the dev server prints, then explore the app.
Changes to files under \`src/\` reload automatically while you work.`,
  };
  return snippets[path] ?? `// ${path}\n// File preview for the ${repo.name} repository.`;
}

/* Tiny warm-monochrome syntax tinting: comments muted italic,
   strings warm brown, numbers olive, keywords semibold ink. */
const TOKEN_RE = /(\/\/.*$)|("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\]|\\.)*`)|\b(import|from|export|default|function|return|const|let|var|if|else|for|of|new|interface|type|extends|implements|async|await|try|catch|throw|switch|case|break|typeof)\b|\b(\d+(?:\.\d+)?)\b/gm;
function highlightLine(line: string, key: number): ReactNode {
  const out: ReactNode[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  TOKEN_RE.lastIndex = 0;
  let i = 0;
  while ((m = TOKEN_RE.exec(line)) !== null) {
    if (m.index > last) out.push(line.slice(last, m.index));
    const cls = m[1] ? 'tok-c' : m[2] ? 'tok-s' : m[3] ? 'tok-k' : 'tok-n';
    out.push(<span key={i++} className={cls}>{m[0]}</span>);
    last = m.index + m[0].length;
  }
  if (last < line.length) out.push(line.slice(last));
  return <span key={key}>{out}</span>;
}

function FolderIcon() {
  return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /></svg>;
}
function FileIcon() {
  return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M6 2h8l4 4v16H6z" /><path d="M14 2v4h4" /></svg>;
}

interface TreeNode { name: string; path: string; dir: boolean; children?: TreeNode[] }
function buildTree(dirKey: string): TreeNode[] {
  return (TREES[dirKey] ?? []).map((row) => {
    const path = dirKey ? `${dirKey}/${row.name}` : row.name;
    return { name: row.name, path, dir: !!row.dir, children: row.dir ? buildTree(path) : undefined };
  });
}

function formatSize(text: string): string {
  const bytes = new Blob([text]).size;
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

/**
 * GitHub-style repository viewer (owner instructions 2026-11-21),
 * shared by the Developer Workspace (full page) and Student Profile
 * project cards (inside a modal). Public repositories only.
 * Interactive throughout: a collapsible file-tree sidebar, nested
 * folder navigation with a clickable breadcrumb, a full code viewer
 * (line numbers, Raw toggle, Copy), a Commits tab, an interactive
 * Star toggle, and the About sidebar with a language bar. Read-only —
 * there are no write actions.
 */
export default function RepoViewer({ repo }: { repo: RepoRef }) {
  const [tab, setTab] = useState<'code' | 'commits' | 'issues' | 'prs' | 'discussion'>('code');
  const [discussion, setDiscussion] = useState<DiscComment[]>(() => [
    {
      author: 'Farhana Islam', creator: false, time: '1 hour ago',
      text: 'The live map view is impressive. One question on @src/pages/MapTracker.tsx — does the polling pause when the tab is hidden?',
      replies: [{ author: repo.ownerName ?? repo.owner, creator: true, time: '42 minutes ago', text: 'Good catch — not yet. The interval keeps running in the background; I will gate it on document visibility in the next commit.' }],
    },
    {
      author: 'Tanvir Hasan', creator: false, time: '3 hours ago',
      text: 'Setup from the README worked first try, and the structure in @src/App.tsx is easy to follow.',
      replies: [],
    },
  ]);
  const [draft, setDraft] = useState('');
  const [mention, setMention] = useState<{ start: number; query: string } | null>(null);
  const [discReplyTo, setDiscReplyTo] = useState<number | null>(null);
  const [discReplyDraft, setDiscReplyDraft] = useState('');
  const [path, setPath] = useState<string[]>([]);
  const [openFile, setOpenFile] = useState<string | null>(null);
  const [raw, setRaw] = useState(false);
  const [copied, setCopied] = useState(false);
  const [starred, setStarred] = useState(false);
  const [treeOpen, setTreeOpen] = useState(true);
  const [expanded, setExpanded] = useState<Set<string>>(new Set(['src']));

  const tree = useMemo(() => buildTree(''), []);
  const dirKey = path.join('/');
  const rows = TREES[dirKey] ?? [];
  const topics = [repo.language.toLowerCase().replace(/\s+/g, '-'), 'student-project', 'public', repo.name.split('-')[0]];
  const content = openFile ? fileContents(repo, openFile) : '';
  const lines = useMemo(() => content.split('\n'), [content]);

  const goTo = (segments: string[]) => { setPath(segments); setOpenFile(null); setRaw(false); };
  const openRow = (row: FileRow) => {
    if (row.dir) goTo([...path, row.name]);
    else { setOpenFile([...path, row.name].join('/')); setRaw(false); }
  };
  const toggleExpand = (p: string) => setExpanded((prev) => { const next = new Set(prev); if (next.has(p)) next.delete(p); else next.add(p); return next; });

  const copyCode = async () => {
    try { await navigator.clipboard.writeText(content); setCopied(true); setTimeout(() => setCopied(false), 1600); }
    catch { setCopied(false); }
  };

  const renderTree = (nodes: TreeNode[], depth: number): ReactNode => nodes.map((node) => (
    <div key={node.path}>
      <button type="button" className="rv-node" style={{ paddingLeft: 6 + depth * 14 }}
        aria-current={openFile === node.path || (!node.dir && false)}
        onClick={() => {
          if (node.dir) { toggleExpand(node.path); goTo(node.path.split('/')); }
          else { setOpenFile(node.path); setRaw(false); setPath(node.path.split('/').slice(0, -1)); }
        }}>
        {node.dir ? <span className="rv-caret">{expanded.has(node.path) ? '▾' : '▸'}</span> : <span className="rv-caret" />}
        {node.dir ? <FolderIcon /> : <FileIcon />}{node.name}
      </button>
      {node.dir && expanded.has(node.path) && node.children && renderTree(node.children, depth + 1)}
    </div>
  ));

  const openMentionedFile = (filePath: string) => {
    setTab('code');
    setPath(filePath.split('/').slice(0, -1));
    setOpenFile(filePath);
    setRaw(false);
  };
  const renderDiscText = (text: string): ReactNode => text.split(/(@[\w./-]+)/g).map((part, i) => {
    if (part.startsWith('@') && ALL_FILES.includes(part.slice(1))) {
      const filePath = part.slice(1);
      return <button key={i} type="button" className="rv-filetag" title={`Open ${filePath}`} onClick={() => openMentionedFile(filePath)}>{part}</button>;
    }
    return <span key={i}>{part}</span>;
  });
  const onDraftChange = (value: string, caret: number) => {
    setDraft(value);
    const at = value.lastIndexOf('@', caret - 1);
    if (at >= 0 && (at === 0 || /\s/.test(value[at - 1]))) {
      const query = value.slice(at + 1, caret);
      if (!/\s/.test(query) && query.length <= 40) { setMention({ start: at, query }); return; }
    }
    setMention(null);
  };
  const pickMention = (file: string) => {
    if (!mention) return;
    const after = draft.slice(mention.start + 1 + mention.query.length);
    setDraft(`${draft.slice(0, mention.start)}@${file} ${after}`);
    setMention(null);
  };
  const mentionOptions = mention ? ALL_FILES.filter((f) => f.toLowerCase().includes(mention.query.toLowerCase())).slice(0, 6) : [];
  const discTotal = discussion.reduce((n, c) => n + 1 + c.replies.length, 0);
  const postDiscComment = () => {
    const text = draft.trim();
    if (!text) return;
    setDiscussion((d) => [...d, { author: 'You', creator: !!repo.viewerIsOwner, time: 'Just now', text, replies: [] }]);
    setDraft(''); setMention(null);
  };
  const postDiscReply = (idx: number) => {
    const text = discReplyDraft.trim();
    if (!text) return;
    setDiscussion((d) => d.map((c, i) => i === idx ? { ...c, replies: [...c.replies, { author: 'You', creator: !!repo.viewerIsOwner, time: 'Just now', text }] } : c));
    setDiscReplyDraft(''); setDiscReplyTo(null);
  };
  const discAvatar = (name: string) => { const parts = name.trim().split(/\s+/).filter(Boolean); return (parts[0]?.[0] ?? '?') + (parts.length > 1 ? parts[parts.length - 1][0] : ''); };

  const about = (
    <aside className="rv-about">
      <h2>About</h2>
      <p>{repo.description}</p>
      <div className="rv-topics">{topics.map((t) => <span key={t} className="rv-pill">{t}</span>)}</div>
      <div className="rv-meta">
        <span><span className="rv-dot" aria-hidden="true" />{repo.language}</span>
        <span>★ {repo.stars + (starred ? 1 : 0)} stars</span>
        <span>Fork {repo.forks} forks</span>
        <span>Public repository — anyone can view this code.</span>
      </div>
      <h2 style={{ marginTop: 14 }}>Languages</h2>
      <div className="rv-langbar" aria-hidden="true">
        <span style={{ flex: 68, background: 'var(--text-primary)' }} />
        <span style={{ flex: 22, background: 'var(--text-secondary)' }} />
        <span style={{ flex: 10, background: 'var(--border)' }} />
      </div>
      <div className="rv-meta">
        <span>{repo.language} 68%</span>
        <span>CSS 22%</span>
        <span>Other 10%</span>
      </div>
    </aside>
  );

  return (
    <div>
      <style>{RV_CSS}</style>
      <div className="rv-head">
        <span className="rv-name"><span className="rv-owner">{repo.owner} / </span><strong>{repo.name}</strong></span>
        <span className="rv-pill">Public</span>
        <span className="rv-counts">
          <button type="button" className="rv-count" aria-pressed={starred} onClick={() => setStarred((v) => !v)}>{starred ? '★ Starred' : '★ Star'} · {repo.stars + (starred ? 1 : 0)}</button>
          <span className="rv-count">Fork · {repo.forks}</span>
        </span>
      </div>
      <div className="rv-tabs" role="tablist" aria-label="Repository tabs">
        <button type="button" role="tab" aria-selected={tab === 'code'} onClick={() => setTab('code')}>Code</button>
        <button type="button" role="tab" aria-selected={tab === 'commits'} onClick={() => setTab('commits')}>Commits · {COMMITS.length}</button>
        <button type="button" role="tab" aria-selected={tab === 'issues'} onClick={() => setTab('issues')}>Issues · {ISSUES.length}</button>
        <button type="button" role="tab" aria-selected={tab === 'prs'} onClick={() => setTab('prs')}>Pull requests · {PRS.length}</button>
        <button type="button" role="tab" aria-selected={tab === 'discussion'} onClick={() => setTab('discussion')}>Discussion · {discTotal}</button>
      </div>

      {tab === 'issues' && <div style={{ marginTop: 8 }}>{ISSUES.map((i) => <div key={i.n} className="rv-issue"><strong>#{i.n}</strong><span>{i.title}</span><span className="rv-pill">{i.label}</span></div>)}</div>}
      {tab === 'prs' && <div style={{ marginTop: 8 }}>{PRS.map((pr) => <div key={pr.n} className="rv-issue"><strong>#{pr.n}</strong><span>{pr.title}</span><span className="secondary">opened by {pr.author}</span></div>)}</div>}
      {tab === 'discussion' && (
        <div style={{ marginTop: 6 }}>
          <div className="rv-disc-composer">
            <label htmlFor="rv-disc-box" style={{ display: 'block', fontWeight: 600, marginBottom: 6 }}>Comment on this project</label>
            <textarea id="rv-disc-box" rows={3} value={draft}
              placeholder="Write a comment… type @ to tag a file"
              onChange={(e) => onDraftChange(e.target.value, e.target.selectionStart ?? e.target.value.length)}
              onKeyDown={(e) => { if (e.key === 'Escape') setMention(null); }} />
            {mention && mentionOptions.length > 0 && (
              <div className="rv-mention-list" role="listbox" aria-label="Repository files">
                {mentionOptions.map((f) => (
                  <button key={f} type="button" role="option" aria-selected="false" onClick={() => pickMention(f)}><FileIcon />{f}</button>
                ))}
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
              <button type="button" className="btn btn-sm btn-primary" disabled={!draft.trim()} onClick={postDiscComment}>Comment</button>
            </div>
          </div>
          {discussion.map((c, idx) => (
            <div key={idx} className="rv-disc-comment">
              <span className="rv-disc-avatar" aria-hidden="true">{discAvatar(c.author)}</span>
              <div className="rv-disc-main">
                <p className="rv-disc-head"><strong>{c.author}</strong>{c.creator && <span className="rv-creator">Creator</span>}<span className="secondary">{c.time}</span></p>
                <p className="rv-disc-body">{renderDiscText(c.text)}</p>
                <div className="rv-disc-actions">
                  <button type="button" aria-expanded={discReplyTo === idx} onClick={() => { setDiscReplyTo(discReplyTo === idx ? null : idx); setDiscReplyDraft(''); }}>Reply</button>
                </div>
                {c.replies.length > 0 && <div className="rv-disc-replies">{c.replies.map((r, ri) => (
                  <div key={ri} className="rv-disc-comment" style={{ borderBottom: 'none', padding: '8px 0' }}>
                    <span className="rv-disc-avatar" aria-hidden="true">{discAvatar(r.author)}</span>
                    <div className="rv-disc-main">
                      <p className="rv-disc-head"><strong>{r.author}</strong>{r.creator && <span className="rv-creator">Creator</span>}<span className="secondary">{r.time}</span></p>
                      <p className="rv-disc-body">{renderDiscText(r.text)}</p>
                    </div>
                  </div>
                ))}</div>}
                {discReplyTo === idx && (
                  <form style={{ display: 'flex', gap: 8, marginTop: 8 }} onSubmit={(e) => { e.preventDefault(); postDiscReply(idx); }}>
                    <input aria-label={`Reply to ${c.author}`} value={discReplyDraft} onChange={(e) => setDiscReplyDraft(e.target.value)} className="input-search" style={{ flex: 1 }} placeholder={`Reply to ${c.author}...`} autoFocus />
                    <button type="submit" className="btn btn-sm btn-primary" disabled={!discReplyDraft.trim()}>Reply</button>
                  </form>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
      {tab === 'commits' && <div style={{ marginTop: 8 }}>{COMMITS.map((c) => (
        <div key={c.hash} className="rv-commit-row">
          <span className="rv-hash">{c.hash}</span>
          <span>{c.message}<span className="secondary"> — {repo.owner}</span></span>
          <span className="secondary">{c.when}</span>
        </div>))}
      </div>}

      {tab === 'code' && (
        <div className="rv-layout">
          {treeOpen ? (
            <div className="rv-tree">
              <div className="rv-tree-head"><strong>Files</strong><button type="button" className="rv-tree-toggle" onClick={() => setTreeOpen(false)}>Hide</button></div>
              {renderTree(tree, 0)}
            </div>
          ) : (
            <div><button type="button" className="rv-tree-toggle" onClick={() => setTreeOpen(true)}>Show files</button></div>
          )}
          <div>
            <div className="rv-branchbar">
              <span className="rv-branch">⎇ main</span>
              <span className="rv-commit"><strong>{repo.owner}</strong> — {COMMITS[0].message} · {COMMITS[0].when}</span>
            </div>
            <p className="rv-crumb" aria-label="Breadcrumb">
              <button type="button" onClick={() => goTo([])}><strong>{repo.name}</strong></button>
              {path.map((seg, i) => (
                <span key={i}><span className="rv-sep"> / </span><button type="button" onClick={() => goTo(path.slice(0, i + 1))}>{seg}</button></span>
              ))}
              {openFile && <span><span className="rv-sep"> / </span><strong>{openFile.split('/').pop()}</strong></span>}
            </p>
            {openFile ? <>
              <div className="rv-filebar">
                <span className="rv-fpath">{openFile}</span>
                <span className="rv-fmeta">{lines.length} lines · {formatSize(content)}</span>
                <span className="rv-factions">
                  <button type="button" className="rv-codebtn" aria-pressed={raw} onClick={() => setRaw((v) => !v)}>Raw</button>
                  <button type="button" className="rv-codebtn" onClick={copyCode}>{copied ? 'Copied!' : 'Copy code'}</button>
                  <button type="button" className="rv-codebtn" onClick={() => setOpenFile(null)}>← Back to repository</button>
                </span>
              </div>
              <div className="rv-code">
                {raw ? <pre className="rv-code-raw">{content}</pre> : (
                  <table><tbody>{lines.map((line, i) => (
                    <tr key={i}><td className="rv-ln">{i + 1}</td><td className="rv-lc">{highlightLine(line, i)}</td></tr>
                  ))}</tbody></table>
                )}
              </div>
            </> : <>
              <div className="rv-table" role="table" aria-label="Repository files">
                {rows.map((row) => (
                  <button key={row.name} type="button" className="rv-row" onClick={() => openRow(row)}>
                    <span className="rv-fname">{row.dir ? <FolderIcon /> : <FileIcon />}{row.name}{row.dir ? '/' : ''}</span>
                    <span className="rv-msg">{row.msg}</span>
                    <span className="rv-when">{row.when}</span>
                  </button>
                ))}
              </div>
              {path.length === 0 && <article className="rv-readme">
                <h1>{repo.name}</h1>
                <p>{repo.description}</p>
                <h2>Overview</h2>
                <p>{repo.name} is a public student project built with {repo.language}. It is developed in the open so classmates can follow the work, reuse the patterns, and suggest improvements through issues and pull requests.</p>
                <h2>Key features</h2>
                <ul>
                  <li>Clean, focused implementation of the core idea</li>
                  <li>Documented setup that runs in a few minutes</li>
                  <li>Small, readable components that are easy to extend</li>
                  <li>Public issue tracker for ideas and bug reports</li>
                </ul>
                <h2>Installation</h2>
                <pre>{`git clone https://github.com/${repo.owner}/${repo.name}.git\ncd ${repo.name}\nnpm install\nnpm run dev`}</pre>
                <h2>Usage</h2>
                <p>Open the local address the dev server prints, then explore the app. Changes to files under <code>src/</code> reload automatically while you work.</p>
              </article>}
            </>}
          </div>
          {about}
        </div>
      )}
    </div>
  );
}

export function slugify(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'project';
}

/** Stable presentation numbers for a profile project until the real
 *  GitHub API supplies them. */
export function repoStats(seed: string): { stars: number; forks: number } {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return { stars: 5 + (h % 55), forks: 1 + (h % 11) };
}

/**
 * Live repository viewer (2026-11-22): the same warm viewer chrome over
 * the REAL GitHub API via the backend proxy (GET /api/workspace/github/
 * repos/{owner}/{repo}, /contents, /file, /commits). Only public
 * repositories can be opened. The Discussion tab is UniLink-native and
 * persisted (GET/POST /api/workspace/discussions) — it is not GitHub
 * Issues; @path mentions autocomplete from the repository's files and
 * jump to the code view.
 */
export function LiveRepoViewer({ owner, name }: { owner: string; name: string }) {
  const qc = useQueryClient();
  const fullName = `${owner}/${name}`;
  const [tab, setTab] = useState<'code' | 'commits' | 'discussion'>('code');
  const [dir, setDir] = useState('');
  const [openPath, setOpenPath] = useState<string | null>(null);
  const [raw, setRaw] = useState(false);
  const [draft, setDraft] = useState('');
  const [replyTo, setReplyTo] = useState<any>(null);
  const [mentionQuery, setMentionQuery] = useState<string | null>(null);
  const [knownPaths, setKnownPaths] = useState<string[]>([]);
  const [postError, setPostError] = useState<string | null>(null);

  const meta = useQuery({ queryKey: ['gh-repo', owner, name], queryFn: () => apiGet<any>(`/workspace/github/repos/${owner}/${name}`), retry: false });
  const contents = useQuery({ queryKey: ['gh-contents', owner, name, dir], queryFn: () => apiGet<any[]>(`/workspace/github/repos/${owner}/${name}/contents?path=${encodeURIComponent(dir)}`), retry: false, enabled: tab === 'code' && !openPath });
  const file = useQuery({ queryKey: ['gh-file', owner, name, openPath], queryFn: () => apiGet<any>(`/workspace/github/repos/${owner}/${name}/file?path=${encodeURIComponent(openPath!)}`), retry: false, enabled: !!openPath });
  const commits = useQuery({ queryKey: ['gh-commits', owner, name], queryFn: () => apiGet<any[]>(`/workspace/github/repos/${owner}/${name}/commits`), retry: false, enabled: tab === 'commits' });
  const discussions = useQuery({ queryKey: ['gh-discussions', fullName], queryFn: () => apiGet<any[]>(`/workspace/discussions?repo=${encodeURIComponent(fullName)}`), retry: false, enabled: tab === 'discussion' });
  useEffect(() => {
    if (contents.data) setKnownPaths((prev) => Array.from(new Set([...prev, ...contents.data.filter((e: any) => e.type === 'file').map((e: any) => e.path as string)])));
  }, [contents.data]);

  const postMut = useMutation({
    mutationFn: () => {
      const mention = draft.match(/@([\w./-]+)/);
      const fileRef = mention && knownPaths.includes(mention[1]) ? mention[1] : undefined;
      return apiPost('/workspace/discussions', { repo: fullName, body: draft.trim(), parentId: replyTo?.id ?? undefined, fileRef });
    },
    onSuccess: () => { setDraft(''); setReplyTo(null); setPostError(null); qc.invalidateQueries({ queryKey: ['gh-discussions', fullName] }); },
    onError: (e) => setPostError(errorMessage(e)),
  });

  const openFile = (path: string) => { setTab('code'); setOpenPath(path); setDir(path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''); };
  const renderBody = (body: string) => body.split(/(@[\w./-]+)/g).map((part, i) =>
    part.startsWith('@') && knownPaths.includes(part.slice(1))
      ? <button key={i} type="button" className="rv-codebtn" onClick={() => openFile(part.slice(1))}>{part}</button>
      : <span key={i}>{part}</span>);

  const repo = meta.data?.repo;
  const segments = dir ? dir.split('/') : [];
  const lines = (file.data?.content ?? '').split('\n');
  const tops = (discussions.data ?? []).filter((d: any) => !d.parentId);
  const repliesOf = (id: number) => (discussions.data ?? []).filter((d: any) => d.parentId === id);
  const mentionSuggestions = mentionQuery === null ? [] : knownPaths.filter((p) => p.toLowerCase().includes(mentionQuery.toLowerCase())).slice(0, 6);

  return (
    <div>
      <style>{RV_CSS}</style>
      {meta.isPending ? <Skeleton /> : meta.isError ? <ErrorState message={errorMessage(meta.error)} onRetry={() => meta.refetch()} /> : repo && <>
        <div className="rv-head">
          <span className="rv-owner">{owner} /</span> <strong className="rv-name">{name}</strong>
          <span className="rv-pill">Public</span>
          <span className="rv-counts">
            <span className="rv-count">★ {repo.stars}</span>
            <span className="rv-count">Fork {repo.forks}</span>
          </span>
        </div>
        {repo.description && <p className="secondary" style={{ margin: '4px 0 0' }}>{repo.description}</p>}
        <div className="rv-tabs" role="tablist" aria-label="Repository sections">
          <button type="button" role="tab" aria-selected={tab === 'code'} onClick={() => setTab('code')}>Code</button>
          <button type="button" role="tab" aria-selected={tab === 'commits'} onClick={() => setTab('commits')}>Commits</button>
          <button type="button" role="tab" aria-selected={tab === 'discussion'} onClick={() => setTab('discussion')}>Discussion{discussions.data ? ` (${discussions.data.length})` : ''}</button>
        </div>

        {tab === 'code' && (
          <div style={{ marginTop: 14 }}>
            <div className="rv-branchbar">
              <span className="rv-branch">{repo.defaultBranch || 'main'}</span>
              <a className="secondary" href={repo.htmlUrl} target="_blank" rel="noreferrer">View on GitHub ↗</a>
            </div>
            <p className="rv-crumb">
              <button type="button" onClick={() => { setDir(''); setOpenPath(null); }}>{name}</button>
              {segments.map((seg, i) => (
                <span key={i}><span className="rv-sep"> / </span><button type="button" onClick={() => { setDir(segments.slice(0, i + 1).join('/')); setOpenPath(null); }}>{seg}</button></span>
              ))}
              {openPath && <span><span className="rv-sep"> / </span><strong>{openPath.split('/').pop()}</strong></span>}
            </p>
            {openPath ? (
              file.isPending ? <Skeleton /> : file.isError ? <ErrorState message={errorMessage(file.error)} onRetry={() => file.refetch()} /> : <>
                <div className="rv-filebar">
                  <span className="rv-fpath">{openPath}</span>
                  <span className="rv-fmeta">{lines.length} lines</span>
                  <span className="rv-factions">
                    <button type="button" className="rv-codebtn" aria-pressed={raw} onClick={() => setRaw((v) => !v)}>Raw</button>
                    <button type="button" className="rv-codebtn" onClick={() => { void navigator.clipboard?.writeText(file.data?.content ?? ''); }}>Copy code</button>
                    <button type="button" className="rv-codebtn" onClick={() => setOpenPath(null)}>Back to files</button>
                  </span>
                </div>
                {raw
                  ? <div className="rv-code"><pre style={{ margin: 0, padding: 14, fontSize: '.84rem', overflow: 'auto' }}>{file.data?.content ?? ''}</pre></div>
                  : <div className="rv-code"><table><tbody>{lines.map((ln: string, i: number) => <tr key={i}><td className="rv-ln">{i + 1}</td><td><pre style={{ margin: 0, fontSize: '.84rem', lineHeight: 1.6 }}>{ln || ' '}</pre></td></tr>)}</tbody></table></div>}
              </>
            ) : contents.isPending ? <Skeleton /> : contents.isError ? <ErrorState message={errorMessage(contents.error)} onRetry={() => contents.refetch()} /> : (
              <div className="rv-table">
                {dir && <button type="button" className="rv-row" onClick={() => setDir(segments.slice(0, -1).join('/'))}><span className="rv-fname">..</span><span className="rv-msg">Parent directory</span><span /></button>}
                {(contents.data ?? []).map((e: any) => (
                  <button key={e.path} type="button" className="rv-row" onClick={() => e.type === 'dir' ? setDir(e.path) : setOpenPath(e.path)}>
                    <span className="rv-fname">{e.type === 'dir' ? '📁' : '📄'} {e.name}</span>
                    <span className="rv-msg">{e.type === 'dir' ? 'Folder' : `${Math.max(1, Math.round((e.size ?? 0) / 1024))} KB`}</span>
                    <span className="rv-when" />
                  </button>
                ))}
              </div>
            )}
            {!openPath && !dir && meta.data?.readme && (
              <section className="rv-readme" aria-label="README"><h2>README.md</h2><pre style={{ whiteSpace: 'pre-wrap', fontSize: '.88rem' }}>{meta.data.readme}</pre></section>
            )}
          </div>
        )}

        {tab === 'commits' && (
          commits.isPending ? <Skeleton /> : commits.isError ? <ErrorState message={errorMessage(commits.error)} onRetry={() => commits.refetch()} />
          : (commits.data ?? []).length === 0 ? <EmptyState title="No commits" body="This repository has no commits yet." />
          : <div style={{ marginTop: 14 }}>{(commits.data ?? []).map((c: any) => (
              <div key={c.sha} className="rv-disc-comment">
                <div className="rv-disc-main">
                  <p className="rv-disc-head"><strong>{c.message}</strong></p>
                  <p className="rv-disc-body secondary">{c.authorName || 'Unknown'} · <code>{c.sha?.slice(0, 7)}</code>{c.createdAt ? ` · ${new Date(c.createdAt).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })}` : ''}</p>
                </div>
              </div>))}</div>
        )}

        {tab === 'discussion' && (
          <div style={{ marginTop: 6 }}>
            <p className="secondary">UniLink discussion for this repository — questions and answers from verified students, saved on UniLink (separate from GitHub Issues). Type @ to mention a file.</p>
            {discussions.isPending ? <Skeleton /> : discussions.isError ? <ErrorState message={errorMessage(discussions.error)} onRetry={() => discussions.refetch()} />
            : tops.length === 0 ? <EmptyState title="No discussion yet" body="Ask the first question about this repository." />
            : tops.map((d: any) => (
              <div key={d.id}>
                <div className="rv-disc-comment">
                  <span className="rv-disc-avatar" aria-hidden="true">{(d.authorName || 'S').split(' ').map((w: string) => w[0]).slice(0, 2).join('')}</span>
                  <div className="rv-disc-main">
                    <p className="rv-disc-head"><strong>{d.authorName}</strong>{d.authorIsCreator && <span className="rv-creator">Creator</span>}<span className="secondary">{d.createdAt ? new Date(d.createdAt).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : ''}</span></p>
                    <p className="rv-disc-body">{renderBody(d.body)}</p>
                    <button type="button" className="rv-codebtn" onClick={() => { setReplyTo(d); setDraft(''); }}>Reply</button>
                  </div>
                </div>
                {repliesOf(d.id).map((r: any) => (
                  <div key={r.id} className="rv-disc-comment" style={{ marginLeft: 42 }}>
                    <span className="rv-disc-avatar" aria-hidden="true">{(r.authorName || 'S').split(' ').map((w: string) => w[0]).slice(0, 2).join('')}</span>
                    <div className="rv-disc-main">
                      <p className="rv-disc-head"><strong>{r.authorName}</strong>{r.authorIsCreator && <span className="rv-creator">Creator</span>}<span className="secondary">{r.createdAt ? new Date(r.createdAt).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : ''}</span></p>
                      <p className="rv-disc-body">{renderBody(r.body)}</p>
                    </div>
                  </div>
                ))}
              </div>
            ))}
            <form style={{ marginTop: 16, position: 'relative' }} onSubmit={(e) => { e.preventDefault(); if (draft.trim()) postMut.mutate(); }}>
              {replyTo && <p className="secondary">Replying to {replyTo.authorName} <button type="button" className="rv-codebtn" onClick={() => setReplyTo(null)}>Cancel reply</button></p>}
              <label htmlFor="live-disc-input" style={{ display: 'block', fontWeight: 600, marginBottom: 6 }}>{replyTo ? 'Write a reply' : 'Join the discussion'}</label>
              <textarea id="live-disc-input" className="input-search" style={{ width: '100%', minHeight: 76 }} value={draft}
                onChange={(e) => { setDraft(e.target.value); const m = e.target.value.match(/(^|\s)@([\w./-]*)$/); setMentionQuery(m ? m[2] : null); }}
                placeholder="Ask a question or share an answer… type @ to mention a file" />
              {mentionSuggestions.length > 0 && (
                <div style={{ border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', background: 'var(--surface)', marginTop: 4 }}>
                  {mentionSuggestions.map((p) => <button key={p} type="button" className="rv-node" onClick={() => { setDraft((d) => d.replace(/@([\w./-]*)$/, `@${p} `)); setMentionQuery(null); }}><code>@{p}</code></button>)}
                </div>
              )}
              {postError && <p role="alert" className="field-error">{postError}</p>}
              <div style={{ marginTop: 10 }}><button type="submit" className="btn btn-primary" disabled={!draft.trim() || postMut.isPending}>{replyTo ? 'Post reply' : 'Post comment'}</button></div>
            </form>
          </div>
        )}
      </>}
    </div>
  );
}
