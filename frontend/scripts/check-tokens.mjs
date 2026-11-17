import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';
const root = new URL('..', import.meta.url).pathname;
const hex = /#[0-9a-fA-F]{3,8}\b/;
let bad=[];
function walk(d){ for(const e of readdirSync(d)){ const p=join(d,e); if(statSync(p).isDirectory()){ if(e==='node_modules'||e==='dist') continue; walk(p);} else if(/\.(ts|tsx|css|html)$/.test(e) && !p.endsWith('src/design/tokens.css')){ const t=readFileSync(p,'utf8'); if(hex.test(t)) bad.push(p); } } }
walk(join(root,'src')); for (const f of ['index.html','admin.html']) { const t2=readFileSync(join(root,f),'utf8'); if(hex.test(t2)) bad.push(f); }
if(bad.length){ console.error('Raw hex outside tokens.css:', bad); process.exit(1);} console.log('Token check passed: no raw hex outside src/design/tokens.css');
