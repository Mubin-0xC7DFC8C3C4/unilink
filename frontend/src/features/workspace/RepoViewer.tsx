import { useEffect, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { EmptyState, ErrorState, Skeleton } from '../../design/primitives';

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
.rv-branchbar{display:flex;justify-content:space-between;gap:12px;align-items:center;margin:2px 0 10px;flex-wrap:wrap}
.rv-branch{border:1px solid var(--border);border-radius:999px;padding:4px 12px;font-size:.88rem;background:var(--surface-muted)}
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
.rv-node{display:flex;gap:6px;align-items:center;width:100%;background:none;border:none;padding:4px 6px;cursor:pointer;color:inherit;font:inherit;font-size:.88rem;text-align:left;border-radius:var(--radius-sm)}
.rv-node:hover{background:var(--surface-muted)}
.rv-code{border:1px solid var(--border);border-radius:0 0 var(--radius-md) var(--radius-md);overflow:auto;background:var(--surface)}
.rv-code table{border-collapse:collapse;width:100%}
.rv-code td{padding:0 12px 0 0;vertical-align:top}
.rv-code .rv-ln{color:var(--text-secondary);text-align:right;user-select:none;width:1%;white-space:nowrap;padding-left:14px;font-size:.8rem;line-height:1.6}
.rv-readme{border:1px solid var(--border);border-radius:var(--radius-md);padding:20px 22px;margin-top:18px}
.rv-readme h1{font-size:1.35rem;margin:0 0 10px}
.rv-readme h2{font-size:1.02rem;margin:18px 0 6px}
.rv-readme h3{font-size:.95rem;margin:14px 0 6px}
.rv-readme p{color:var(--text-secondary);margin:0 0 8px}
.rv-readme ul,.rv-readme ol{margin:6px 0;padding-left:20px;color:var(--text-secondary)}
.rv-readme pre{background:var(--surface-muted);border:1px solid var(--border);border-radius:var(--radius-sm);padding:12px;overflow:auto;font-size:.84rem}
.rv-readme code{font-family:ui-monospace,Menlo,monospace;font-size:.86em;background:var(--surface-muted);border-radius:4px;padding:0 4px}
.rv-readme pre code{background:none;padding:0}
.rv-readme a{color:inherit;text-decoration:underline}
.rv-readme blockquote{border-left:3px solid var(--border);margin:8px 0;padding:2px 12px;color:var(--text-secondary)}
.rv-readme blockquote p{margin:4px 0}
.rv-readme hr{border:none;border-top:1px solid var(--border);margin:16px 0}
.rv-about{border:1px solid var(--border);border-radius:var(--radius-md);padding:16px;margin-top:18px}
.rv-about h2{font-size:.95rem;margin:0 0 8px}
.rv-about p{color:var(--text-secondary);font-size:.9rem;margin:0 0 10px}
.rv-topics{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px}
.rv-meta{color:var(--text-secondary);font-size:.88rem;display:flex;flex-direction:column;gap:5px}
.rv-dot{width:9px;height:9px;border-radius:50%;background:var(--text-secondary);display:inline-block;margin-right:5px}
.rv-creator{background:var(--primary);color:var(--on-primary);border-radius:999px;padding:1px 8px;font-size:.68rem;font-weight:700;letter-spacing:.02em}
.rv-disc-comment{display:flex;gap:10px;padding:12px 0;border-bottom:1px solid var(--border)}
.rv-disc-avatar{width:32px;height:32px;border-radius:50%;background:var(--primary);color:var(--on-primary);display:inline-flex;align-items:center;justify-content:center;font-size:.72rem;font-weight:700;flex:none}
.rv-disc-main{flex:1;min-width:0}
.rv-disc-head{margin:0;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.rv-disc-body{margin:3px 0 0}
.rv-disc-actions{display:flex;gap:14px;margin-top:5px}
.rv-disc-actions button{background:none;border:none;padding:0;cursor:pointer;color:var(--text-secondary);font:inherit;font-size:.78rem;font-weight:600}
@media (max-width:760px){.rv-row{grid-template-columns:minmax(120px,1fr) auto}.rv-row .rv-msg{display:none}}
`;

/* ------------------------------------------------------------------ */
/* Small safe Markdown renderer (D-203): the README comes from the   */
/* real GitHub API as raw markdown and is rendered here — no markdown */
/* dependency, and no dangerouslySetInnerHTML anywhere: React escapes */
/* every text node, and links are only built for http(s) URLs.       */
/* Supports fenced code, headings, bold/italic, inline code, links,  */
/* lists, blockquotes and horizontal rules. Nested emphasis inside   */
/* bold/italic is not parsed.                                        */
/* ------------------------------------------------------------------ */

const INLINE_RE = /(\*\*[^*\n]+\*\*|\*[^*\n]+\*|\[[^\]]+\]\(https?:\/\/[^)\s]+\)|`[^`\n]+`)/g;

function parseInline(text: string, keyPrefix: string): ReactNode[] {
  const out: ReactNode[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  let k = 0;
  INLINE_RE.lastIndex = 0;
  while ((m = INLINE_RE.exec(text)) !== null) {
    if (m.index > last) out.push(text.slice(last, m.index));
    const tok = m[0];
    const key = `${keyPrefix}-${k++}`;
    if (tok.startsWith('**')) out.push(<strong key={key}>{tok.slice(2, -2)}</strong>);
    else if (tok.startsWith('*')) out.push(<em key={key}>{tok.slice(1, -1)}</em>);
    else if (tok.startsWith('`')) out.push(<code key={key}>{tok.slice(1, -1)}</code>);
    else {
      const lm = tok.match(/^\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)$/);
      if (lm) out.push(<a key={key} href={lm[2]} target="_blank" rel="noreferrer">{lm[1]}</a>);
      else out.push(tok);
    }
    last = m.index + tok.length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

export function renderMarkdown(md: string): ReactNode {
  const lines = md.replace(/\r\n?/g, '\n').split('\n');
  const blocks: ReactNode[] = [];
  let i = 0;
  let key = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (/^\s*$/.test(line)) { i++; continue; }
    if (/^```/.test(line)) {
      const buf: string[] = [];
      i++;
      while (i < lines.length && !/^```\s*$/.test(lines[i])) { buf.push(lines[i]); i++; }
      i++;
      blocks.push(<pre key={key++}><code>{buf.join('\n')}</code></pre>);
      continue;
    }
    const h = line.match(/^(#{1,3})\s+(.*)$/);
    if (h) {
      const text = h[2].trim();
      blocks.push(h[1].length === 1 ? <h1 key={key++}>{parseInline(text, `h${key}`)}</h1>
        : h[1].length === 2 ? <h2 key={key++}>{parseInline(text, `h${key}`)}</h2>
        : <h3 key={key++}>{parseInline(text, `h${key}`)}</h3>);
      i++;
      continue;
    }
    if (/^(---|\*\*\*)\s*$/.test(line)) { blocks.push(<hr key={key++} />); i++; continue; }
    if (/^>\s?/.test(line)) {
      const buf: string[] = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) { buf.push(lines[i].replace(/^>\s?/, '')); i++; }
      blocks.push(<blockquote key={key++}>{buf.map((b, bi) => <p key={bi}>{parseInline(b, `q${key}-${bi}`)}</p>)}</blockquote>);
      continue;
    }
    if (/^\s*[-*]\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) { items.push(lines[i].replace(/^\s*[-*]\s+/, '')); i++; }
      blocks.push(<ul key={key++}>{items.map((t, ii) => <li key={ii}>{parseInline(t, `u${key}-${ii}`)}</li>)}</ul>);
      continue;
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) { items.push(lines[i].replace(/^\s*\d+\.\s+/, '')); i++; }
      blocks.push(<ol key={key++}>{items.map((t, ii) => <li key={ii}>{parseInline(t, `o${key}-${ii}`)}</li>)}</ol>);
      continue;
    }
    const buf: string[] = [];
    while (i < lines.length && !/^\s*$/.test(lines[i])
      && !/^(#{1,3}\s|```|>\s?|\s*[-*]\s+|\s*\d+\.\s+|---\s*$|\*\*\*\s*$)/.test(lines[i])) { buf.push(lines[i]); i++; }
    blocks.push(<p key={key++}>{buf.map((b, bi) => <span key={bi}>{bi > 0 && <br />}{parseInline(b, `p${key}-${bi}`)}</span>)}</p>);
  }
  return <>{blocks}</>;
}

/**
 * Live repository viewer (2026-11-22, D-203 2026-11-25): the same warm
 * viewer chrome over the REAL GitHub API via the backend proxy (GET
 * /api/workspace/github/repos/{owner}/{repo}, /contents, /file,
 * /commits). Only public repositories can be opened. The Discussion tab
 * is UniLink-native and persisted (GET/POST /api/workspace/discussions)
 * — it is not GitHub Issues; @path mentions autocomplete from the
 * repository's files and jump to the code view. The README is rendered
 * as Markdown from the API text, and the About panel shows the real
 * description, language, stars/forks and default branch. The old
 * fixture viewer (hardcoded tree/commits/issues, deterministic fake
 * stats) was retired by D-203 — nothing here is sample data.
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
              <span className="rv-branch">⎇ {repo.defaultBranch || 'main'}</span>
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
              <section className="rv-readme" aria-label="README">{renderMarkdown(meta.data.readme)}</section>
            )}
            {!openPath && !dir && (
              <aside className="rv-about" aria-label="About this repository">
                <h2>About</h2>
                {repo.description && <p>{repo.description}</p>}
                <div className="rv-meta">
                  {repo.language && <span><span className="rv-dot" aria-hidden="true" />{repo.language}</span>}
                  <span>★ {repo.stars} stars</span>
                  <span>Fork {repo.forks} forks</span>
                  <span>Default branch: {repo.defaultBranch || 'main'}</span>
                  <span>Public repository — anyone can view this code.</span>
                </div>
                {repo.htmlUrl && <p style={{ marginTop: 10 }}><a href={repo.htmlUrl} target="_blank" rel="noreferrer">View on GitHub ↗</a></p>}
              </aside>
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
