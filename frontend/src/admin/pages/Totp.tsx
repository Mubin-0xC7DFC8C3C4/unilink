import { useState } from 'react'; import { useMutation } from '@tanstack/react-query'; import { useNavigate } from 'react-router-dom';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader } from '../../design/primitives';
export default function Totp(){ const [code,setCode]=useState(''); const [secret,setSecret]=useState(''); const nav=useNavigate();
  const enrol=useMutation({ mutationFn: ()=>apiPost<{secret?:string; otpauthUrl?:string}>('/admin/totp/enrol',{}), onSuccess:(d)=>setSecret(d.secret||'') });
  const verify=useMutation({ mutationFn: ()=>apiPost('/admin/totp/verify',{code}), onSuccess: ()=>nav('/admin') });
  return <div className="container" style={{maxWidth:480,paddingTop:48}}><PageHeader eyebrow="Admin Portal · Two-factor" title="TOTP verification" lead="Enter the 6-digit code from your authenticator app. First login: enrol TOTP and save your recovery codes." />
  <Button onClick={()=>enrol.mutate()}>Enrol TOTP</Button>{secret && <p role="status">Enrolment secret (scan or enter in your authenticator app): <code>{secret}</code>. Save your recovery codes when the backend provides them.</p>}
  <form onSubmit={e=>{e.preventDefault(); verify.mutate();}} style={{marginTop:16}}><Field label="TOTP code" htmlFor="totp"><Input id="totp" inputMode="numeric" value={code} onChange={e=>setCode(e.target.value)} /></Field>
  {verify.isError && <p role="alert" className="field-error">{errorMessage(verify.error)}</p>}<Button type="submit" variant="primary">Verify and enter admin</Button></form></div>;
}
