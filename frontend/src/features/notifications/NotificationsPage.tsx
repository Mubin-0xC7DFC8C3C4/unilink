import { useMutation, useQueryClient } from '@tanstack/react-query'; import { ListPage } from '../shared'; import { RuledList, Button } from '../../design/primitives'; import { apiPost, errorMessage } from '../../api/client'; import { useState } from 'react'; import { NOTIFICATION_TYPES } from '../../api/types';
/** Preferences are one POST /api/notifications/preferences per type, with
 *  {type, emailEnabled, inAppEnabled} — not a PATCH to /me/…. The list
 *  rows carry `readFlag`, not `read`. */
type PrefState = Record<string, {email: boolean; inApp: boolean}>;
const defaultPrefs = (): PrefState => Object.fromEntries(NOTIFICATION_TYPES.map(t=>[t,{email:true,inApp:true}]));
export default function NotificationsPage(){ const qc=useQueryClient(); const [saved,setSaved]=useState(false);
  const [prefsState,setPrefsState]=useState<PrefState>(defaultPrefs);
  const prefs = useMutation({ mutationFn: async (p: PrefState)=>{ for (const type of NOTIFICATION_TYPES){ await apiPost('/notifications/preferences',{type, emailEnabled: p[type].email, inAppEnabled: p[type].inApp}); } }, onSuccess: ()=>{ setSaved(true); } });
  const markRead = useMutation({ mutationFn: (id:string|number)=>apiPost(`/notifications/${id}/read`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['notifications']}) });
  const markAll = useMutation({ mutationFn: ()=>apiPost('/notifications/read-all',{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['notifications']}) });
  return <div><ListPage<any> eyebrow="Notifications" title="Notifications" lead="Decisions, mentions, team updates and event reminders." queryKey={['notifications']} path="/notifications" emptyTitle="No notifications" emptyBody="You have no notifications yet." render={items=><><div style={{marginBottom:8}}><Button onClick={()=>markAll.mutate()} disabled={markAll.isPending}>Mark all read</Button></div><RuledList>{items.map((n:any)=><li key={n.id}><strong>{n.title||n.message||'Notification'}</strong><p className="secondary">{n.body||''} {n.createdAt?`· ${n.createdAt}`:''} {n.readFlag?'· Read':''}</p>{!n.readFlag && <Button onClick={()=>markRead.mutate(n.id)}>Mark read</Button>}</li>)}</RuledList></>} />
  <div className="container"><section className="card-group"><h2 className="title">Preferences</h2><p className="secondary">Choose which notifications you receive, per type. Security emails cannot be disabled server-side.</p>
  {NOTIFICATION_TYPES.map(t=><div key={t} style={{display:'flex',gap:16,alignItems:'center',marginBottom:8}}><span style={{minWidth:200}}>{t}</span><label><input type="checkbox" checked={prefsState[t].email} onChange={e=>setPrefsState(s=>({...s,[t]:{...s[t],email:e.target.checked}}))} /> Email</label><label><input type="checkbox" checked={prefsState[t].inApp} onChange={e=>setPrefsState(s=>({...s,[t]:{...s[t],inApp:e.target.checked}}))} /> In app</label></div>)}
  <Button variant="primary" onClick={()=>prefs.mutate(prefsState)} disabled={prefs.isPending}>Save preferences</Button>
  {prefs.isError && <p role="alert" className="field-error">{errorMessage(prefs.error)}</p>}{saved && <p role="status">Preferences saved.</p>}</section></div></div>;
}
