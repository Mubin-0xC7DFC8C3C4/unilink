import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { applyTheme, initialTheme, toggleTheme, type Theme } from '../design/theme';
const Ctx = createContext<{theme:Theme; toggle:()=>void}>({theme:'light', toggle:()=>{}});
export const useTheme=()=>useContext(Ctx);
export function ThemeProvider({children}:{children:ReactNode}){ const [theme,setTheme]=useState<Theme>(initialTheme); useEffect(()=>applyTheme(theme),[theme]); return <Ctx.Provider value={{theme, toggle:()=>setTheme(toggleTheme)}}>{children}</Ctx.Provider>; }
