import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { apiGet } from '../api/client';
export interface SessionUser { id?: string|number; fullName?: string; email?: string; role?: string; verified?: boolean; university?: string; department?: string; mustChangePassword?: boolean }
interface AuthCtx { user: SessionUser|null; setUser:(u:SessionUser|null)=>void; ready: boolean; idleNotice: boolean; dismissIdleNotice: ()=>void }
const Ctx = createContext<AuthCtx>({user:null,setUser:()=>{},ready:false,idleNotice:false,dismissIdleNotice:()=>{}});
export const useAuth=()=>useContext(Ctx);
/** I-10 session restore + CSRF priming + idle notice.
 *  On load: GET /api/csrf primes the CSRF cookie, then GET /api/auth/me (or
 *  the admin app's /api/admin/auth/me via restorePath) restores the
 *  server-side session, so a reload no longer drops the client-side user.
 *  `ready` is false until the restore settles, so RequireAuth never flashes
 *  the signed-out fallback for a user who is actually signed in.
 *  Idle notice: after idleMinutes without mouse/keyboard/click/scroll
 *  activity a banner warns the session is about to end (student: 2 h,
 *  admin: 30 min server-side). It is a warning only — it does not extend the
 *  server session. Any activity dismisses it and restarts the timer. */
export function AuthProvider({children, restorePath='/auth/me', idleMinutes=105, idleNoticeText='You have been idle for a while. For security, your session ends after 2 hours of inactivity — save anything important, then keep working or log in again.'}:{children:ReactNode; restorePath?: string; idleMinutes?: number; idleNoticeText?: string}){
  const [user,setUser]=useState<SessionUser|null>(null); const [ready,setReady]=useState(false);
  const [idleNotice,setIdleNotice]=useState(false); const lastActivity=useRef(Date.now());
  useEffect(()=>{ apiGet('/csrf').catch(()=>{});
    apiGet<Record<string,unknown>>(restorePath).then(d=>{
      // Admin /me answers {adminId, email, role, mfaVerified}; an admin who
      // has not finished MFA must not count as signed in for the guards.
      if (d.mfaVerified===false){ setUser(null); return; }
      // D-094/D-098: /me reports mustChangePassword, so a reload still
      // lands the user on the change-password screen (via the Layout /
      // AdminLayout redirect) instead of silently restoring a restricted
      // session that then 403s on every call.
      setUser({id: (d.id ?? d.adminId) as string|number|undefined, fullName: d.fullName as string|undefined, email: d.email as string|undefined, role: d.role as string|undefined, verified: d.state==='VERIFIED', mustChangePassword: d.mustChangePassword===true});
    }).catch(()=>setUser(null)).finally(()=>setReady(true));
  },[restorePath]);
  useEffect(()=>{ const poke=()=>{ lastActivity.current=Date.now(); setIdleNotice(false); };
    const events=['mousemove','keydown','click','scroll'] as const; events.forEach(e=>window.addEventListener(e,poke));
    const t=setInterval(()=>{ if (Date.now()-lastActivity.current > idleMinutes*60_000) setIdleNotice(true); },30_000);
    return ()=>{ events.forEach(e=>window.removeEventListener(e,poke)); clearInterval(t); };
  },[idleMinutes]);
  return <Ctx.Provider value={{user,setUser,ready,idleNotice,dismissIdleNotice:()=>setIdleNotice(false)}}>
    {idleNotice && <div role="status" style={{background:'#fff3cd',color:'#5c3d00',padding:'10px 16px',textAlign:'center',fontSize:14}}>{idleNoticeText} <button type="button" onClick={()=>setIdleNotice(false)} style={{marginLeft:8}}>Dismiss</button></div>}
    {children}</Ctx.Provider>;
}
export function RequireAuth({children, fallback}:{children:ReactNode; fallback:ReactNode}){ const {user,ready}=useAuth(); if(!ready) return null; return user? <>{children}</> : <>{fallback}</>; }
