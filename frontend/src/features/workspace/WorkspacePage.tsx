import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, apiGet, errorMessage } from '../../api/client';
import { EmptyState, ErrorState, Skeleton } from '../../design/primitives';
import { IconGitHub } from '../../design/icons';
import { LiveRepoViewer } from './RepoViewer';

const WS_CSS = `
.ws2-layout{display:grid;grid-template-columns:216px minmax(0,1fr);gap:28px;align-items:start;margin-top:8px}
.ws2-side{border-right:1px solid var(--border);padding-right:16px}
.ws2-eyebrow{font-size:.72rem;letter-spacing:.08em;font-weight:600;color:var(--text-secondary);margin:0 0 8px;text-transform:uppercase}
.ws2-nav{display:flex;flex-direction:column;gap:2px;margin-bottom:20px}
.ws2-nav button{display:flex;gap:8px;align-items:center;text-align:left;background:none;border:1px solid transparent;border-radius:var(--radius-sm);padding:8px 10px;cursor:pointer;color:inherit;font:inherit}
.ws2-nav button[aria-current="true"]{background:var(--surface-muted);border-color:var(--border);font-weight:600}
.ws2-nav-locked{opacity:.4;pointer-events:none;user-select:none}
.ws2-note{color:var(--text-secondary);font-size:.88rem;margin:4px 0 0}
.ws2-title{font-size:1.9rem;line-height:1.2;margin:4px 0 10px;max-width:560px}
.ws2-lead{color:var(--text-secondary);max-width:560px;margin:0 0 22px}
.ws2-card{border:1px solid var(--border);border-radius:var(--radius-md);padding:20px;max-width:640px}
.ws2-card-head{display:flex;gap:12px;align-items:center;margin-bottom:6px}
.ws2-mark{width:40px;height:40px;color:var(--text-primary);display:inline-flex;align-items:center;justify-content:center;flex:none}
.ws2-card h2{margin:0;font-size:1.12rem}
.ws2-card p{color:var(--text-secondary);margin:0 0 6px}
.ws2-checks{list-style:none;margin:14px 0;padding:0;border-top:1px solid var(--border)}
.ws2-checks li{padding:9px 0;border-bottom:1px solid var(--border);font-size:.93rem}
.ws2-actions{display:flex;gap:12px;align-items:center;margin-top:16px;flex-wrap:wrap}
.ws2-headrow{display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap;margin-bottom:18px}
.ws2-pill{display:inline-flex;gap:8px;align-items:center;border:1px solid var(--border);border-radius:999px;padding:5px 12px;font-size:.88rem;font-weight:600}
.repo-card{border:1px solid var(--border);border-radius:var(--radius-md);padding:16px;margin-bottom:12px;max-width:640px}
.repo-card h3{margin:0 0 4px;font-size:1rem;display:flex;gap:8px;align-items:center}
.repo-title-btn{background:none;border:none;padding:0;font:inherit;font-weight:700;cursor:pointer;color:inherit}
.repo-pill{border:1px solid var(--border);border-radius:999px;padding:1px 9px;font-size:.72rem;font-weight:600}
.repo-card p{color:var(--text-secondary);margin:0 0 8px}
.repo-meta{display:flex;gap:16px;align-items:center;color:var(--text-secondary);font-size:.86rem;flex-wrap:wrap}
.repo-dot{width:9px;height:9px;border-radius:50%;background:var(--text-secondary);display:inline-block;margin-right:5px}
.repo-actions{display:flex;gap:8px;margin-top:12px}
@media (max-width:760px){.ws2-layout{grid-template-columns:1fr}.ws2-side{border-right:none;padding-right:0}}
`;

type GhRepo = { owner: string; name: string; fullName: string; description?: string; language?: string; stars: number; forks: number };

function RepoCard({ repo, onOpen }: { repo: GhRepo; onOpen: (r: GhRepo) => void }) {
  return (
    <article className="repo-card">
      <h3><button type="button" className="repo-title-btn" onClick={() => onOpen(repo)}>{repo.owner} / {repo.name}</button><span className="repo-pill">Public</span></h3>
      {repo.description && <p>{repo.description}</p>}
      <div className="repo-meta">
        {repo.language && <span><span className="repo-dot" aria-hidden="true" />{repo.language}</span>}
        <span>★ {repo.stars}</span>
        <span>Fork {repo.forks}</span>
      </div>
      <div className="repo-actions">
        <button type="button" className="btn btn-sm" onClick={() => onOpen(repo)}>View details</button>
      </div>
    </article>
  );
}

/**
 * Developer Workspace (owner-approved design). The GitHub connection is
 * REAL (2026-11-22): status comes from GET /api/workspace/github/
 * status; "Authorize GitHub" fetches the OAuth authorize URL from the
 * backend and sends the browser to GitHub — the code exchange happens
 * server-side and the token is stored encrypted. Repositories are the
 * student's own public repositories from the GitHub API, opened in the
 * live repository viewer. When the server has no GitHub OAuth app
 * configured, the card says so instead of simulating a connection.
 */
export default function WorkspacePage() {
  const qc = useQueryClient();
  const location = useLocation();
  const [view, setView] = useState<'overview' | 'projects'>('overview');
  const [openRepo, setOpenRepo] = useState<GhRepo | null>(null);
  const [authError, setAuthError] = useState<string | null>(null);
  const returnNote = new URLSearchParams(location.search).get('github');

  const status = useQuery({ queryKey: ['github-status'], queryFn: () => apiGet<any>('/workspace/github/status'), retry: false });
  useEffect(() => { if (returnNote) { void qc.invalidateQueries({ queryKey: ['github-status'] }); void qc.invalidateQueries({ queryKey: ['github-repos'] }); } }, [returnNote, qc]);
  const connected = !!status.data?.connected;
  const configured = !!status.data?.configured;
  const username = status.data?.username as string | undefined;

  const repos = useQuery({ queryKey: ['github-repos'], queryFn: () => apiGet<GhRepo[]>('/workspace/github/repos'), retry: false, enabled: connected });
  const authorize = useMutation({
    mutationFn: () => apiGet<{ url: string }>('/workspace/github/authorize'),
    onSuccess: (d) => { window.location.assign(d.url); },
    onError: (e) => setAuthError(errorMessage(e)),
  });
  const disconnect = useMutation({
    mutationFn: () => api('/workspace/github/connection', { method: 'DELETE' }),
    onSuccess: () => { setOpenRepo(null); void qc.invalidateQueries({ queryKey: ['github-status'] }); void qc.invalidateQueries({ queryKey: ['github-repos'] }); },
    onError: (e) => setAuthError(errorMessage(e)),
  });

  return (
    <div className="container">
      <style>{WS_CSS}</style>
      {openRepo ? (
        <div>
          <button type="button" className="btn btn-sm" style={{ marginBottom: 16 }} onClick={() => setOpenRepo(null)}>← Back to Workspace</button>
          <LiveRepoViewer owner={openRepo.owner} name={openRepo.name} />
        </div>
      ) : (
        <div className="ws2-layout">
          <aside className="ws2-side" aria-label="Workspace sidebar">
            <p className="ws2-eyebrow">Workspace</p>
            <nav className={`ws2-nav${connected ? '' : ' ws2-nav-locked'}`} aria-label="Workspace sections" aria-disabled={!connected}>
              <button type="button" aria-current={view === 'overview'} disabled={!connected} onClick={() => setView('overview')}>Overview</button>
              <button type="button" aria-current={view === 'projects'} disabled={!connected} onClick={() => setView('projects')}>My Projects</button>
            </nav>
            <p className="ws2-eyebrow">GitHub</p>
            {connected ? <>
              <p style={{ margin: 0, fontWeight: 600 }}>Connected (@{username})</p>
              <p className="ws2-note">Your public repositories are shown in your Workspace.</p>
            </> : <>
              <p style={{ margin: 0, fontWeight: 600 }}>Not connected</p>
              <p className="ws2-note">Project areas unlock after GitHub authorization.</p>
            </>}
          </aside>
          <div>
            {returnNote === 'connected' && <p role="status" className="secondary">GitHub connected. Your public repositories are listed below.</p>}
            {returnNote === 'error' && <p role="alert" className="field-error">GitHub authorization did not complete. Please try again.</p>}
            {status.isPending ? <Skeleton /> : status.isError ? <ErrorState message={errorMessage(status.error)} onRetry={() => status.refetch()} /> : !connected ? <>
              <p className="ws2-eyebrow">Developer Workspace</p>
              <h1 className="ws2-title">Connect your GitHub. Showcase your real code.</h1>
              <p className="ws2-lead">Link your GitHub account once to import public repositories directly onto your UniLink profile and collaborate with verified peers.</p>
              <section className="ws2-card" aria-label="GitHub Repository Integration">
                <div className="ws2-card-head">
                  <span className="ws2-mark" aria-hidden="true"><IconGitHub size={36} /></span>
                  <h2>GitHub Repository Integration</h2>
                </div>
                <p>Grant read-only access to display your public repositories, tech stack, and READMEs inside UniLink.</p>
                <ul className="ws2-checks">
                  <li>✓ Read-only access to your <strong>public</strong> repositories only</li>
                  <li>✓ No write permission is requested — ever</li>
                  <li>✓ Disconnect any time from Developer Workspace</li>
                </ul>
                {configured ? (
                  <div className="ws2-actions">
                    <button type="button" className="btn btn-primary" disabled={authorize.isPending} onClick={() => authorize.mutate()}>Authorize GitHub</button>
                    <span className="secondary">You will be sent to GitHub to approve, then returned here.</span>
                  </div>
                ) : (
                  <p className="ws2-note" style={{ marginTop: 14 }}>{status.data?.message || 'GitHub integration is not configured on this server yet.'}</p>
                )}
                {authError && <p role="alert" className="field-error">{authError}</p>}
              </section>
            </> : <>
              <p className="ws2-eyebrow">Developer Workspace</p>
              <h1 className="ws2-title">{view === 'overview' ? 'Your GitHub Workspace' : 'My Projects'}</h1>
              <div className="ws2-headrow">
                <span className="ws2-pill"><IconGitHub size={16} /> Connected as @{username}</span>
                <button type="button" className="btn btn-sm" disabled={disconnect.isPending} onClick={() => disconnect.mutate()}>Disconnect</button>
              </div>
              {repos.isPending ? <Skeleton /> : repos.isError ? <ErrorState message={errorMessage(repos.error)} onRetry={() => repos.refetch()} />
                : (repos.data ?? []).length === 0 ? <EmptyState title="No public repositories" body="Repositories you make public on GitHub will appear here." />
                : (repos.data ?? []).map((r) => <RepoCard key={r.fullName} repo={r} onOpen={setOpenRepo} />)}
              {view === 'projects' && <p className="ws2-note" style={{ marginTop: 8 }}>Repositories you add to <Link to="/profile/me">your profile</Link> open in the same viewer.</p>}
              {authError && <p role="alert" className="field-error">{authError}</p>}
            </>}
          </div>
        </div>
      )}
    </div>
  );
}
