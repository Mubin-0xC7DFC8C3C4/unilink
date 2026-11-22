import { useEffect, useState } from 'react'; import { useNavigate } from 'react-router-dom'; import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'; import { apiGet, apiPost, apiPut, apiDelete, api, errorMessage } from '../../api/client';
import { Button, Field, Input } from '../../design/primitives'; import { useTheme } from '../../app/ThemeContext'; import { useAuth } from '../../app/auth'; import { NOTIFICATION_TYPES } from '../../api/types';
import type { UserProfile, ProjectItem, EducationItem, AchievementItem } from '../../api/types';
import { IconX } from '../../design/icons';
import { parseLinks, composeLinks } from '../profile/links';

export type SettingsTab = 'Edit Profile' | 'Appearance' | 'Behavior' | 'Notifications' | 'Privacy';
const TABS: SettingsTab[] = ['Edit Profile', 'Appearance', 'Behavior', 'Notifications', 'Privacy'];

const SM_CSS = `
.sm-backdrop{position:fixed;inset:0;background:rgba(20,20,15,.45);z-index:90;display:flex;align-items:flex-start;justify-content:center;padding:40px 16px;overflow:auto}
.sm-modal{position:relative;background:var(--background);border:1px solid var(--border);border-radius:var(--radius-md);width:100%;max-width:820px;padding:20px;box-shadow:0 16px 48px rgba(0,0,0,.25)}
.sm-close{position:absolute;top:12px;right:12px;background:none;border:none;cursor:pointer;color:inherit;display:inline-flex;padding:6px}
.sm-layout{display:grid;grid-template-columns:172px minmax(0,1fr);gap:22px;margin-top:6px}
.sm-nav{display:flex;flex-direction:column;gap:2px}
.sm-nav button{display:flex;align-items:center;gap:8px;width:100%;text-align:left;background:none;border:1px solid transparent;border-radius:var(--radius-sm);padding:9px 10px;cursor:pointer;color:inherit;font:inherit}
.sm-nav button[aria-current="true"]{background:var(--surface-muted);border-color:var(--border);font-weight:600}
.sm-title{margin:2px 0 12px;font-size:1.15rem}
.sm-section{margin-top:26px}
.sm-section:first-of-type{margin-top:4px}
.sm-pills{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px}
.sm-pill{display:inline-flex;gap:6px;align-items:center;border:1px solid var(--border);border-radius:999px;padding:2px 6px 2px 10px;font-size:.85rem}
.sm-pill button{background:none;border:none;cursor:pointer;color:inherit;font:inherit;padding:0 2px;line-height:1}
.sm-item{display:flex;gap:8px;align-items:baseline;border-top:1px solid var(--border);padding:8px 0}
.sm-item:first-of-type{border-top:none}
@media (max-width:640px){.sm-layout{grid-template-columns:1fr}.sm-nav{flex-direction:row;flex-wrap:wrap}}
`;

type PrefState = Record<string, { email: boolean; inApp: boolean }>;
const defaultPrefs = (): PrefState => Object.fromEntries(NOTIFICATION_TYPES.map((t) => [t, { email: true, inApp: true }]));

/**
 * Portfolio manager for Settings → Edit Profile (owner instruction
 * 2026-11-21): Education, Pinned Projects and Achievements are
 * added/edited/deleted here — the profile page itself is a read-only
 * showcase. Uses the real /profiles/me/{projects|education|
 * achievements} endpoints; saves invalidate both the modal's lists
 * and the profile page's query keys so the showcase stays in sync.
 */
function PortfolioManager() {
  const qc = useQueryClient();
  const projects = useQuery({ queryKey: ['my-projects'], queryFn: () => apiGet<ProjectItem[]>('/profiles/me/projects'), retry: false });
  const education = useQuery({ queryKey: ['my-education'], queryFn: () => apiGet<EducationItem[]>('/profiles/me/education'), retry: false });
  const achievements = useQuery({ queryKey: ['my-achievements'], queryFn: () => apiGet<AchievementItem[]>('/profiles/me/achievements'), retry: false });
  const [kind, setKind] = useState<'project' | 'education' | 'achievement'>('project');
  const [title, setTitle] = useState(''); const [desc, setDesc] = useState(''); const [url, setUrl] = useState(''); const [year, setYear] = useState('');
  const [editId, setEditId] = useState<string | number | null>(null);
  const refresh = () => { ['my-projects', 'my-education', 'my-achievements', 'projects', 'education', 'achievements'].forEach((k) => qc.invalidateQueries({ queryKey: [k] })); };
  const reset = () => { setTitle(''); setDesc(''); setUrl(''); setYear(''); setEditId(null); };
  const save = useMutation({ mutationFn: () => {
    const path = kind === 'project' ? '/profiles/me/projects' : kind === 'education' ? '/profiles/me/education' : '/profiles/me/achievements';
    const body = kind === 'project' ? { title, description: desc, url } : kind === 'education' ? { institution: title, degree: desc, year: year ? Number(year) : undefined } : { title, description: desc, year: year ? Number(year) : undefined };
    return editId ? api(`${path}/${editId}`, { method: 'PUT', body: JSON.stringify(body) }) : apiPost(path, body);
  }, onSuccess: () => { refresh(); reset(); } });
  const del = useMutation({ mutationFn: ({ k, id }: { k: string; id: string | number }) => apiDelete(`/profiles/me/${k}/${id}`), onSuccess: refresh });
  const startEdit = (k: 'project' | 'education' | 'achievement', item: any) => { setKind(k); setEditId(item.id);
    if (k === 'project') { setTitle(item.title || ''); setDesc(item.description || ''); setUrl(item.url || ''); }
    if (k === 'education') { setTitle(item.institution || ''); setDesc(item.degree || ''); setYear(item.year ? String(item.year) : ''); }
    if (k === 'achievement') { setTitle(item.title || ''); setDesc(item.description || ''); setYear(item.year ? String(item.year) : ''); } };
  const err = save.error || del.error;
  const list = (heading: string, q: typeof projects, render: (i: any) => string, k: 'project' | 'education' | 'achievement', delKey: string) => (
    <div className="sm-section">
      <h3 className="title">{heading}</h3>
      {q.isPending ? <p className="secondary">Loading…</p> : q.isError ? <p role="alert" className="field-error">{errorMessage(q.error)}</p>
        : (q.data || []).length === 0 ? <p className="secondary">Nothing here yet — add your first entry below.</p>
        : (q.data || []).map((i: any) => <div key={i.id} className="sm-item"><span style={{ flex: 1 }}>{render(i)}</span><Button onClick={() => startEdit(k, i)}>Edit</Button><Button onClick={() => del.mutate({ k: delKey, id: i.id })}>Delete</Button></div>)}
    </div>
  );
  return <>
    {list('Education', education, (e) => `${e.degree || ''}${e.institution ? ` — ${e.institution}` : ''}${e.year ? ` · ${e.year}` : ''}`, 'education', 'education')}
    {list('Pinned Projects', projects, (p) => p.title || '', 'project', 'projects')}
    {list('Achievements', achievements, (a) => `${a.title || ''}${a.year ? ` · ${a.year}` : ''}`, 'achievement', 'achievements')}
    <div className="sm-section">
      <h3 className="title">{editId ? 'Edit' : 'Add'} portfolio item</h3>
      <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>{(['project', 'education', 'achievement'] as const).map((k) => <button key={k} type="button" className={kind === k ? 'btn btn-primary btn-sm' : 'btn btn-sm'} onClick={() => { setKind(k); setEditId(null); }}>{k}</button>)}</div>
      <Field label={kind === 'education' ? 'Institution' : 'Title'} htmlFor="ep-title"><Input id="ep-title" value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
      <Field label={kind === 'education' ? 'Degree' : 'Description'} htmlFor="ep-desc"><Input id="ep-desc" value={desc} onChange={(e) => setDesc(e.target.value)} /></Field>
      {kind === 'project' && <Field label="Link" htmlFor="ep-url"><Input id="ep-url" value={url} onChange={(e) => setUrl(e.target.value)} /></Field>}
      {kind !== 'project' && <Field label="Year" htmlFor="ep-year"><Input id="ep-year" type="number" value={year} onChange={(e) => setYear(e.target.value)} /></Field>}
      {err && <p role="alert" className="field-error">{errorMessage(err)}</p>}
      <div style={{ display: 'flex', gap: 8 }}><Button variant="primary" onClick={() => save.mutate()} disabled={!title.trim() || save.isPending}>{editId ? 'Save changes' : 'Add'}</Button>{editId && <Button onClick={reset}>Cancel</Button>}</div>
    </div>
  </>;
}

/**
 * Settings as a two-column modal (owner instruction 2026-11-21,
 * replacing the standalone Settings page). The first tab is
 * Edit Profile: photos, headline/bio/phone, skills, links and the
 * portfolio manager, all over the real profile endpoints
 * (PUT /profiles/me, photo/cover uploads, POST/DELETE
 * /profiles/me/skills with the /public/skills catalogue,
 * /profiles/me/{projects|education|achievements}). Phone is shown
 * read-only: no endpoint changes a verified phone number. The
 * change-password and delete-account functions live under Behavior
 * (the owner's tab list has no Security tab) so a must-change login
 * still has a working path.
 */
export default function SettingsModal({ open, initialTab, onClose }: { open: boolean; initialTab: SettingsTab; onClose: () => void }) {
  const { theme, toggle } = useTheme(); const { setUser, user } = useAuth(); const nav = useNavigate(); const qc = useQueryClient();
  const [tab, setTab] = useState<SettingsTab>(initialTab);
  const profileQ = useQuery({ queryKey: ['my-profile'], queryFn: () => apiGet<UserProfile>('/profiles/me'), retry: false, enabled: open });
  const catalogueQ = useQuery({ queryKey: ['skill-catalogue'], queryFn: () => apiGet<{ id: number; name: string }[]>('/public/skills'), retry: false, enabled: open });
  const [bio, setBio] = useState(''); const [headline, setHeadline] = useState('');
  const [gh, setGh] = useState(''); const [pf, setPf] = useState(''); const [li, setLi] = useState('');
  const [phoneVisible, setPhoneVisible] = useState(false); const [showDelete, setShowDelete] = useState(false); const [pw, setPw] = useState(''); const [msg, setMsg] = useState('');
  const [newSkill, setNewSkill] = useState('');
  const saveProfile = useMutation({ mutationFn: () => apiPut('/profiles/me', { bio, headline, links: composeLinks({ github: gh, portfolio: pf, linkedin: li }) }), onSuccess: () => { setMsg('Profile saved.'); qc.invalidateQueries({ queryKey: ['my-profile'] }); qc.invalidateQueries({ queryKey: ['user'] }); } });
  const savePrivacy = useMutation({ mutationFn: () => apiPut('/profiles/me', { phoneVisible }), onSuccess: () => setMsg('Privacy saved.') });
  const addSkill = useMutation({ mutationFn: (name: string) => apiPost('/profiles/me/skills', { name }), onSuccess: () => { setNewSkill(''); qc.invalidateQueries({ queryKey: ['my-profile'] }); qc.invalidateQueries({ queryKey: ['skill-catalogue'] }); } });
  const removeSkill = useMutation({ mutationFn: (name: string) => {
    const entry = (catalogueQ.data || []).find((s) => s.name.toLowerCase() === name.toLowerCase());
    if (!entry) throw new Error('That skill is not in the catalogue, so it cannot be removed here.');
    return apiDelete(`/profiles/me/skills/${entry.id}`);
  }, onSuccess: () => qc.invalidateQueries({ queryKey: ['my-profile'] }) });
  const [oldPw, setOldPw] = useState(''); const [newPw, setNewPw] = useState('');
  const changePw = useMutation({ mutationFn: () => apiPost('/auth/change-password', { oldPassword: oldPw, newPassword: newPw }), onSuccess: () => { setUser(null); onClose(); nav('/login'); } });
  const del = useMutation({ mutationFn: () => apiPost('/me/delete', { password: pw }), onSuccess: () => { setUser(null); onClose(); nav('/login'); } });
  const [photo, setPhoto] = useState<File | null>(null); const [cover, setCover] = useState<File | null>(null);
  const uploadPhoto = useMutation({ mutationFn: () => { const fd = new FormData(); fd.append('file', photo!); return api('/profiles/me/photo', { method: 'PUT', body: fd }); }, onSuccess: () => { setPhoto(null); setMsg('Profile photo uploaded.'); } });
  const uploadCover = useMutation({ mutationFn: () => { const fd = new FormData(); fd.append('file', cover!); return api('/profiles/me/cover', { method: 'PUT', body: fd }); }, onSuccess: () => { setCover(null); setMsg('Cover photo uploaded.'); } });
  const [prefsState, setPrefsState] = useState<PrefState>(defaultPrefs);
  const [prefResults, setPrefResults] = useState<{ type: string; ok: boolean }[] | null>(null);
  const [prefSaved, setPrefSaved] = useState(false);
  const prefs = useMutation({ mutationFn: async (p: PrefState) => {
    const settled = await Promise.allSettled(NOTIFICATION_TYPES.map((type) => apiPost('/notifications/preferences', { type, emailEnabled: p[type].email, inAppEnabled: p[type].inApp })));
    return NOTIFICATION_TYPES.map((type, i) => ({ type, ok: settled[i].status === 'fulfilled' }));
  }, onSuccess: (r) => { setPrefResults(r); setPrefSaved(r.every((x) => x.ok)); } });

  useEffect(() => { if (open) { setTab(initialTab); setMsg(''); setShowDelete(false); } }, [open, initialTab]);
  useEffect(() => {
    if (!profileQ.data) return;
    setBio(profileQ.data.bio || ''); setHeadline(profileQ.data.headline || '');
    const parsed = parseLinks(profileQ.data.links);
    setGh(parsed.github); setPf(parsed.portfolio); setLi(parsed.linkedin);
  }, [profileQ.data]);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;
  const mySkills = profileQ.data?.skills || [];
  return (
    <div className="sm-backdrop" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <style>{SM_CSS}</style>
      <div className="sm-modal" role="dialog" aria-modal="true" aria-label="Settings">
        <button type="button" className="sm-close" aria-label="Close settings" onClick={onClose}><IconX /></button>
        <div className="sm-layout">
          <nav className="sm-nav" aria-label="Settings sections">
            {TABS.map((t) => <button key={t} type="button" aria-current={tab === t} onClick={() => setTab(t)}>{t}</button>)}
          </nav>
          <div>
            <h2 className="sm-title">{tab}</h2>
            {tab === 'Edit Profile' && <>
              <div className="sm-section">
                <h3 className="title">Photos</h3>
                <p className="secondary">Add a profile photo and a cover banner — both are optional, and you can change them any time.</p>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}><input aria-label="Profile photo" type="file" accept="image/*" onChange={(e) => setPhoto(e.target.files?.[0] || null)} /><Button onClick={() => photo && uploadPhoto.mutate()} disabled={!photo || uploadPhoto.isPending}>Upload photo</Button></div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}><input aria-label="Cover photo" type="file" accept="image/*" onChange={(e) => setCover(e.target.files?.[0] || null)} /><Button onClick={() => cover && uploadCover.mutate()} disabled={!cover || uploadCover.isPending}>Upload cover</Button></div>
                {(uploadPhoto.isError || uploadCover.isError) && <p role="alert" className="field-error">{errorMessage(uploadPhoto.error || uploadCover.error)}</p>}
              </div>
              <div className="sm-section">
                <h3 className="title">Basic info</h3>
                <Field label="Headline" htmlFor="s-headline"><Input id="s-headline" value={headline} onChange={(e) => setHeadline(e.target.value)} /></Field>
                <Field label="Bio" htmlFor="s-bio"><Input id="s-bio" value={bio} onChange={(e) => setBio(e.target.value)} /></Field>
                <Field label="Phone number" htmlFor="s-phone"><Input id="s-phone" value={profileQ.data?.phone || ''} disabled readOnly /></Field>
                <p className="secondary">Your phone number was verified at sign-up and can't be changed here.</p>
              </div>
              <div className="sm-section">
                <h3 className="title">Skills</h3>
                {mySkills.length === 0 ? <p className="secondary">No skills yet — add your first one below.</p> : <div className="sm-pills">{mySkills.map((s) => <span key={s} className="sm-pill">{s}<button type="button" aria-label={`Remove skill ${s}`} onClick={() => removeSkill.mutate(s)}>×</button></span>)}</div>}
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <Input id="s-new-skill" aria-label="New skill" value={newSkill} onChange={(e) => setNewSkill(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter' && newSkill.trim()) { e.preventDefault(); addSkill.mutate(newSkill.trim()); } }} placeholder="e.g. Python" />
                  <Button onClick={() => newSkill.trim() && addSkill.mutate(newSkill.trim())} disabled={!newSkill.trim() || addSkill.isPending}>Add skill</Button>
                </div>
                {(addSkill.isError || removeSkill.isError) && <p role="alert" className="field-error">{errorMessage(addSkill.error || removeSkill.error)}</p>}
              </div>
              <div className="sm-section">
                <h3 className="title">Links</h3>
                <Field label="GitHub URL" htmlFor="s-gh"><Input id="s-gh" value={gh} onChange={(e) => setGh(e.target.value)} placeholder="https://github.com/you" /></Field>
                <Field label="Portfolio URL" htmlFor="s-pf"><Input id="s-pf" value={pf} onChange={(e) => setPf(e.target.value)} placeholder="https://your-portfolio.example" /></Field>
                <Field label="LinkedIn URL" htmlFor="s-li"><Input id="s-li" value={li} onChange={(e) => setLi(e.target.value)} placeholder="https://www.linkedin.com/in/you" /></Field>
                <Button variant="primary" onClick={() => saveProfile.mutate()}>Save profile</Button>
                {saveProfile.isError && <p role="alert" className="field-error">{errorMessage(saveProfile.error)}</p>}
              </div>
              <PortfolioManager />
            </>}
            {tab === 'Appearance' && <>
              <p className="secondary">Current theme: {theme === 'light' ? 'Light' : 'Dark'}.</p>
              <Button onClick={toggle}>Switch to {theme === 'light' ? 'dark' : 'light'} theme</Button>
            </>}
            {tab === 'Privacy' && <>
              <label><input type="checkbox" checked={phoneVisible} onChange={(e) => setPhoneVisible(e.target.checked)} /> Show my phone number</label>
              <p className="secondary">Your address is never shown to other users.</p>
              <div style={{ marginTop: 8 }}><Button variant="primary" onClick={() => savePrivacy.mutate()}>Save privacy</Button></div>
              {savePrivacy.isError && <p role="alert" className="field-error">{errorMessage(savePrivacy.error)}</p>}
            </>}
            {tab === 'Notifications' && <>
              <p className="secondary">Choose which notifications you receive, per type. Security emails always stay on.</p>
              {NOTIFICATION_TYPES.map((t) => <div key={t} style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 8 }}><span style={{ minWidth: 190 }}>{t}</span><label><input type="checkbox" checked={prefsState[t].email} onChange={(e) => setPrefsState((s) => ({ ...s, [t]: { ...s[t], email: e.target.checked } }))} /> Email</label><label><input type="checkbox" checked={prefsState[t].inApp} onChange={(e) => setPrefsState((s) => ({ ...s, [t]: { ...s[t], inApp: e.target.checked } }))} /> In app</label></div>)}
              <Button variant="primary" onClick={() => { setPrefResults(null); setPrefSaved(false); prefs.mutate(prefsState); }} disabled={prefs.isPending}>Save preferences</Button>
              {prefResults && <div role="status" style={{ marginTop: 8 }}>{prefResults.map((r) => <p key={r.type} className="secondary">{r.type}: {r.ok ? 'saved' : 'failed — not saved, try again'}</p>)}{!prefResults.every((r) => r.ok) && <p role="alert" className="field-error">Some preferences could not be saved: {prefResults.filter((r) => !r.ok).map((r) => r.type).join(', ')}. The types above marked saved did apply.</p>}</div>}
              {prefSaved && <p role="status">Preferences saved.</p>}
            </>}
            {tab === 'Behavior' && <>
              {user?.mustChangePassword && <p role="alert" style={{ background: '#fff3cd', color: '#5c3d00', padding: '10px 16px' }}>You must change your password before continuing — an administrator issued you a temporary password. Change it below, then log in again with the new password.</p>}
              <h3 className="title" style={{ marginTop: 0 }}>Change password</h3>
              <p className="secondary">At least 8 characters, with upper-case, lower-case and a digit. Changing your password signs you out everywhere; log in again with the new password.</p>
              <Field label="Current password" htmlFor="cp-old"><Input id="cp-old" type="password" value={oldPw} onChange={(e) => setOldPw(e.target.value)} autoComplete="current-password" /></Field>
              <Field label="New password" htmlFor="cp-new"><Input id="cp-new" type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} autoComplete="new-password" /></Field>
              <Button variant="primary" onClick={() => changePw.mutate()} disabled={!oldPw || !newPw || changePw.isPending}>Change password</Button>
              {changePw.isError && <p role="alert" className="field-error">{errorMessage(changePw.error)}</p>}
              <h3 className="title" style={{ marginTop: 28 }}>Delete account</h3>
              <p className="secondary">Permanently delete your account and all associated data. You must re-enter your password to confirm.</p>
              {!showDelete ? <Button onClick={() => setShowDelete(true)}>Delete my account…</Button> : <>
                <Field label="Password" htmlFor="del-pw"><Input id="del-pw" type="password" value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
                {del.isError && <p role="alert" className="field-error">{errorMessage(del.error)}</p>}
                <div style={{ display: 'flex', gap: 8 }}><Button variant="primary" onClick={() => del.mutate()} disabled={!pw || del.isPending}>Delete account</Button><Button onClick={() => setShowDelete(false)}>Cancel</Button></div>
              </>}
            </>}
            {msg && <p role="status" style={{ marginTop: 12 }}>{msg}</p>}
          </div>
        </div>
      </div>
    </div>
  );
}
