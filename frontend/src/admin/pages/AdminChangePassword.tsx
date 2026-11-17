import { useState } from 'react'; import { useNavigate } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader } from '../../design/primitives'; import { useAuth } from '../../app/auth';
/** D-093/D-098: admin change-password screen (POST /api/admin/auth/change-password).
 *  Reached after a temporary-password login (TOTP verify redirects here when
 *  mustChangePassword is set) and after a session restore with the flag
 *  (AdminLayout redirects here). Also usable voluntarily from the admin
 *  section. The server requires MFA for this call and invalidates every
 *  session on success, so the admin logs in again with the new password. */
export default function AdminChangePassword(){ const nav=useNavigate(); const { setUser, user }=useAuth();
  const [oldPw,setOldPw]=useState(''); const [newPw,setNewPw]=useState('');
  const mut=useMutation({ mutationFn: ()=>apiPost('/admin/auth/change-password',{oldPassword:oldPw, newPassword:newPw}),
    onSuccess: ()=>{ setUser(null); nav('/admin/login'); } });
  return <div className="container" style={{maxWidth:480,paddingTop:48}}><PageHeader eyebrow="Admin Portal" title="Change password" lead="Admin passwords are at least 12 characters. Changing your password signs you out everywhere; log in again with the new password and your authenticator." />
  {user?.mustChangePassword && <p role="alert" style={{background:'#fff3cd',color:'#5c3d00',padding:'10px 16px'}}>You must change your password before continuing — a Platform Admin issued you a temporary password.</p>}
  <form onSubmit={e=>{e.preventDefault(); mut.mutate();}} noValidate>
  <Field label="Current password" htmlFor="acp-old"><Input id="acp-old" type="password" value={oldPw} onChange={e=>setOldPw(e.target.value)} autoComplete="current-password" /></Field>
  <Field label="New password (at least 12 characters)" htmlFor="acp-new"><Input id="acp-new" type="password" value={newPw} onChange={e=>setNewPw(e.target.value)} autoComplete="new-password" /></Field>
  {mut.isError && <p role="alert" className="field-error">{errorMessage(mut.error)}</p>}
  <Button type="submit" variant="primary" disabled={!oldPw||!newPw||mut.isPending}>Change password</Button></form></div>;
}
