import { useState } from 'react'; import { useNavigate } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query'; import { apiPost, apiPut, api, errorMessage } from '../../api/client';
import { Button, Field, Input, PageHeader, Modal } from '../../design/primitives'; import { useTheme } from '../../app/ThemeContext'; import { useAuth } from '../../app/auth';
export default function SettingsPage(){ const { theme, toggle }=useTheme(); const { setUser, user }=useAuth(); const nav=useNavigate(); const [bio,setBio]=useState(''); const [phoneVisible,setPhoneVisible]=useState(false); const [showDelete,setShowDelete]=useState(false); const [pw,setPw]=useState(''); const [msg,setMsg]=useState('');
  const saveProfile = useMutation({ mutationFn: ()=>apiPut('/profiles/me',{bio}), onSuccess: ()=>setMsg('Profile saved.') });
  const savePrivacy = useMutation({ mutationFn: ()=>apiPut('/profiles/me',{phoneVisible}), onSuccess: ()=>setMsg('Privacy saved.') });
  // D-093/D-094: the student change-password form. The endpoint already
  // existed (POST /api/auth/change-password) but no UI called it, so a
  // must-change login had nowhere to go. The server invalidates every
  // session on success, so the client signs out and returns to /login.
  const [oldPw,setOldPw]=useState(''); const [newPw,setNewPw]=useState('');
  const changePw = useMutation({ mutationFn: ()=>apiPost('/auth/change-password',{oldPassword:oldPw, newPassword:newPw}), onSuccess: ()=>{ setUser(null); nav('/login'); } });
  // Deletion is synchronous server-side (one transaction, D-052): on
  // success the account is already gone, not "being processed". The server
  // also invalidates the session, so clear the client auth state (same
  // setUser(null) + nav('/login') as the Layout logout) — otherwise the
  // client still looks signed in to a deleted account.
  const del = useMutation({ mutationFn: ()=>apiPost('/me/delete',{password:pw}), onSuccess: ()=>{ setUser(null); nav('/login'); } });
  const [photo,setPhoto]=useState<File|null>(null); const [cover,setCover]=useState<File|null>(null);
  const uploadPhoto = useMutation({ mutationFn: ()=>{ const fd=new FormData(); fd.append('file', photo!); return api('/profiles/me/photo',{method:'PUT', body: fd}); }, onSuccess: ()=>{ setPhoto(null); setMsg('Profile photo uploaded.'); } });
  const uploadCover = useMutation({ mutationFn: ()=>{ const fd=new FormData(); fd.append('file', cover!); return api('/profiles/me/cover',{method:'PUT', body: fd}); }, onSuccess: ()=>{ setCover(null); setMsg('Cover photo uploaded.'); } });
  return <div className="container" style={{maxWidth:720}}><PageHeader eyebrow="Settings" title="Settings" lead="Profile, privacy, theme and account. Authoritative data lives on the server — nothing here is stored only in this browser." />
  {user?.mustChangePassword && <p role="alert" style={{background:'#fff3cd',color:'#5c3d00',padding:'10px 16px'}}>You must change your password before continuing — an administrator issued you a temporary password. Change it below, then log in again with the new password.</p>}
  <section className="card-group"><h2 className="title">Change password</h2><p className="secondary">At least 8 characters, with upper-case, lower-case and a digit. Changing your password signs you out everywhere; log in again with the new password.</p>
  <Field label="Current password" htmlFor="cp-old"><Input id="cp-old" type="password" value={oldPw} onChange={e=>setOldPw(e.target.value)} autoComplete="current-password" /></Field>
  <Field label="New password" htmlFor="cp-new"><Input id="cp-new" type="password" value={newPw} onChange={e=>setNewPw(e.target.value)} autoComplete="new-password" /></Field>
  <Button variant="primary" onClick={()=>changePw.mutate()} disabled={!oldPw||!newPw||changePw.isPending}>Change password</Button>
  {changePw.isError && <p role="alert" className="field-error">{errorMessage(changePw.error)}</p>}</section>
  <section className="card-group"><h2 className="title">Profile</h2><Field label="Bio" htmlFor="s-bio"><Input id="s-bio" value={bio} onChange={e=>setBio(e.target.value)} /></Field><Button variant="primary" onClick={()=>saveProfile.mutate()}>Save profile</Button>{saveProfile.isError && <p role="alert" className="field-error">{errorMessage(saveProfile.error)}</p>}</section>
  <section className="card-group"><h2 className="title">Photo and cover</h2><p className="secondary">Images only, up to 2 MB each.</p>
  <div style={{display:'flex',gap:8,alignItems:'center',marginBottom:8}}><input aria-label="Profile photo" type="file" accept="image/*" onChange={e=>setPhoto(e.target.files?.[0]||null)} /><Button onClick={()=>photo && uploadPhoto.mutate()} disabled={!photo||uploadPhoto.isPending}>Upload photo</Button></div>
  <div style={{display:'flex',gap:8,alignItems:'center'}}><input aria-label="Cover photo" type="file" accept="image/*" onChange={e=>setCover(e.target.files?.[0]||null)} /><Button onClick={()=>cover && uploadCover.mutate()} disabled={!cover||uploadCover.isPending}>Upload cover</Button></div>
  {(uploadPhoto.isError||uploadCover.isError) && <p role="alert" className="field-error">{errorMessage(uploadPhoto.error||uploadCover.error)}</p>}</section>
  <section className="card-group"><h2 className="title">Privacy</h2><label><input type="checkbox" checked={phoneVisible} onChange={e=>setPhoneVisible(e.target.checked)} /> Show my phone number</label><p className="secondary">Your address is never shown to other users.</p><div style={{marginTop:8}}><Button onClick={()=>savePrivacy.mutate()}>Save privacy</Button></div>{savePrivacy.isError && <p role="alert" className="field-error">{errorMessage(savePrivacy.error)}</p>}</section>
  <section className="card-group"><h2 className="title">Appearance</h2><p className="secondary">Current theme: {theme}. Your choice is saved to your account when signed in; before login only a theme hint is kept in this browser.</p><Button onClick={toggle}>Switch to {theme==='light'?'dark':'light'} theme</Button></section>
  <section className="card-group"><h2 className="title">Delete account</h2><p className="secondary">Deleting your account anonymises and removes your data server-side (D-052). You must re-enter your password.</p><Button onClick={()=>setShowDelete(true)}>Delete my account…</Button></section>
  {msg && <p role="status">{msg}</p>}
  {showDelete && <Modal title="Delete account" onClose={()=>setShowDelete(false)}><p>This cannot be undone. Enter your password to confirm.</p><Field label="Password" htmlFor="del-pw"><Input id="del-pw" type="password" value={pw} onChange={e=>setPw(e.target.value)} /></Field>{del.isError && <p role="alert" className="field-error">{errorMessage(del.error)}</p>}<Button variant="primary" onClick={()=>del.mutate()} disabled={!pw}>Delete account</Button></Modal>}
  </div>;
}
