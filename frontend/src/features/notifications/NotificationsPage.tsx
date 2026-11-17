import { useMutation, useQueryClient } from '@tanstack/react-query'; import { ListPage } from '../shared'; import { RuledList, Button } from '../../design/primitives'; import { apiPost } from '../../api/client'; import { useState } from 'react'; import { NOTIFICATION_TYPES } from '../../api/types';
/** Preferences are one POST /api/notifications/preferences per type, with
 *  {type, emailEnabled, inAppEnabled} — not a PATCH to /me/…. The list
 *  rows carry `readFlag`, not `read`. */
type PrefState = Record<string, {email: boolean; inApp: boolean}>;
const defaultPrefs = (): PrefState => Object.fromEntries(NOTIFICATION_TYPES.map(t=>[t,{email:true,inApp:true}]));
export default function NotificationsPage(){ const qc=useQueryClient(); const [saved,setSaved]=useState(false);
  const [results,setResults]=useState<{type:string; ok:boolean}[]|null>(null);
  const [prefsState,setPrefsState]=useState<PrefState>(defaultPrefs);
  // One POST per type, but the saves are independent: settle all of them and
  // report per type which applied and which failed, instead of one
  // all-or-nothing message that hides a partial save.
  const prefs = useMutation({ mutationFn: async (p: PrefState)=>{
    const settled = await Promise.allSettled(NOTIFICATION_TYPES.map(type=>apiPost('/notifications/preferences',{type, emailEnabled: p[type].email, inAppEnabled: p[type].inApp})));
    return NOTIFICATION_TYPES.map((type,i)=>({type, ok: settled[i].status==='fulfilled'}));
  }, onSuccess: (r)=>{ setResults(r); setSaved(r.every(x=>x.ok)); } });
  const markRead = useMutation({ mutationFn: (id:string|number)=>apiPost(`/notifications/${id}/read`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['notifications']}) });
  const markAll = useMutation({ mutationFn: ()=>apiPost('/notifications/read-all',{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['notifications']}) });
  return <div><ListPage<any> eyebrow="Notifications" title="Notifications" lead="Decisions, mentions, team updates and event reminders." queryKey={['notifications']} path="/notifications" emptyTitle="No notifications" emptyBody="You have no notifications yet." render={items=><><div style={{marginBottom:8}}><Button onClick={()=>markAll.mutate()} disabled={markAll.isPending}>Mark all read</Button></div><RuledList>{items.map((n:any)=><li key={n.id}><strong>{n.title||n.message||'Notification'}</strong><p className="secondary">{n.body||''} {n.createdAt?`· ${n.createdAt}`:''} {n.readFlag?'· Read':''}</p>{!n.readFlag && <Button onClick={()=>markRead.mutate(n.id)}>Mark read</Button>}</li>)}</RuledList></>} />
  <div className="container"><section className="card-group"><h2 className="title">Preferences</h2><p className="secondary">Choose which notifications you receive, per type. Security emails cannot be disabled server-side.</p>
  {NOTIFICATION_TYPES.map(t=><div key={t} style={{display:'flex',gap:16,alignItems:'center',marginBottom:8}}><span style={{minWidth:200}}>{t}</span><label><input type="checkbox" checked={prefsState[t].email} onChange={e=>setPrefsState(s=>({...s,[t]:{...s[t],email:e.target.checked}}))} /> Email</label><label><input type="checkbox" checked={prefsState[t].inApp} onChange={e=>setPrefsState(s=>({...s,[t]:{...s[t],inApp:e.target.checked}}))} /> In app</label></div>)}
  <Button variant="primary" onClick={()=>{ setResults(null); setSaved(false); prefs.mutate(prefsState); }} disabled={prefs.isPending}>Save preferences</Button>
  {results && <div role="status" style={{marginTop:8}}>{results.map(r=><p key={r.type} className="secondary">{r.type}: {r.ok?'saved':'failed — not saved, try again'}</p>)}{!results.every(r=>r.ok) && <p role="alert" className="field-error">Some preferences could not be saved: {results.filter(r=>!r.ok).map(r=>r.type).join(', ')}. The types above marked saved did apply.</p>}</div>}
  {saved && <p role="status">Preferences saved.</p>}</section></div></div>;
}
