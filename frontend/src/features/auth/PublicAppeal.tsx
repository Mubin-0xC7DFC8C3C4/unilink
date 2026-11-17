import { useState } from 'react'; import { useSearchParams, Link } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query'; import { apiPost, errorMessage } from '../../api/client';
import { Button, Field, PageHeader } from '../../design/primitives';
/** Suspension appeal via the emailed single-use link (D-019, I-13).
 *  Public route — a suspended/banned user has no session, so this page is
 *  unguarded. The token identifies the appeal row the backend created when
 *  the account was suspended or banned; the reason is all the user submits.
 *  targetType/targetId are carried on that row already — the body sends
 *  placeholders the backend ignores for the token flow. */
export default function PublicAppeal(){
  const [params]=useSearchParams(); const token=params.get('token');
  const [reason,setReason]=useState('');
  const submit=useMutation({ mutationFn: ()=>apiPost(`/appeals/public?token=${encodeURIComponent(token||'')}`,{targetType:'USER', targetId: null, reason}) });
  if (!token) return <div className="container"><PageHeader eyebrow="Appeal" title="Appeal a suspension" /><p role="alert" className="field-error">No appeal link was provided. Use the link from the suspension email sent to your account.</p><p><Link className="btn" to="/login">Log in</Link></p></div>;
  return <div className="container" style={{maxWidth:640}}><PageHeader eyebrow="Appeal" title="Appeal a suspension" lead="Your account was suspended or banned. Explain why the decision should be reconsidered — a Platform Admin will review your appeal. The link works once and expires 7 days after it was sent." />
  {submit.isSuccess?<><p role="status">Appeal submitted. A Platform Admin will review it and notify you by email.</p><p><Link className="btn" to="/login">Back to log in</Link></p></>
  :<><Field label="Why should the suspension be reconsidered?" htmlFor="appeal-reason"><textarea id="appeal-reason" className="input-search" style={{width:'100%',minHeight:120}} value={reason} onChange={e=>setReason(e.target.value)} placeholder="Explain what happened and why the account should be restored" /></Field>
  <Button variant="primary" onClick={()=>submit.mutate()} disabled={!reason.trim()||submit.isPending}>Submit appeal</Button>
  {submit.isError && <p role="alert" className="field-error">{errorMessage(submit.error)}</p>}</>}
  </div>;
}
