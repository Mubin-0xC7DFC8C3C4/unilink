import { createContext, useContext, useState, type ReactNode } from 'react';
export interface SessionUser { id?: string|number; fullName?: string; email?: string; role?: string; verified?: boolean; university?: string; department?: string }
const Ctx = createContext<{user: SessionUser|null; setUser:(u:SessionUser|null)=>void}>({user:null,setUser:()=>{}});
export const useAuth=()=>useContext(Ctx);
export function AuthProvider({children}:{children:ReactNode}){ const [user,setUser]=useState<SessionUser|null>(null); return <Ctx.Provider value={{user,setUser}}>{children}</Ctx.Provider>; }
export function RequireAuth({children, fallback}:{children:ReactNode; fallback:ReactNode}){ const {user}=useAuth(); return user? <>{children}</> : <>{fallback}</>; }
