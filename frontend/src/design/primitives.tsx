import { useState, type ReactNode, type ButtonHTMLAttributes, type InputHTMLAttributes, forwardRef } from 'react';
export function Button({children, variant='outline', ...rest}: ButtonHTMLAttributes<HTMLButtonElement> & {variant?:'outline'|'primary'}){ return <button className={variant==='primary'?'btn btn-primary':'btn'} {...rest}>{children}</button>; }
export function Field({label, error, children, htmlFor}:{label:string; error?:string; children:ReactNode; htmlFor?:string}){ return <div className="field"><label htmlFor={htmlFor}>{label}</label>{children}{error?<p className="field-error" role="alert">{error}</p>:null}</div>; }
/** forwardRef: react-hook-form's register() passes a ref callback, and a plain
 *  function component drops it — every RHF form using Input submitted empty
 *  values. Same props and rendering, ref now reaches the <input>. */
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(props, ref){ return <input ref={ref} {...props} />; });
export function StatStrip({stats}:{stats:{label:string; value:string; note?:string}[]}){ return <div className="stat-strip" role="group" aria-label="Statistics">{stats.map(s=><div key={s.label}><div className="stat-cap">{s.label}</div><div className="stat-num">{s.value}</div>{s.note?<div className="body-sm secondary">{s.note}</div>:null}</div>)}</div>; }
export function RuledList({children}:{children:ReactNode}){ return <ul className="ruled-list">{children}</ul>; }
export function EmptyState({title, body, action}:{title:string; body?:string; action?:ReactNode}){ return <div role="status" style={{padding:'32px 0', textAlign:'center'}}><p className="title">{title}</p>{body?<p className="secondary">{body}</p>:null}{action}</div>; }
export function Skeleton({label='Loading'}:{label?:string}){ return <div aria-busy="true" aria-label={label}><div className="skeleton" style={{height:24, marginBottom:8}} /><div className="skeleton" style={{height:16, width:'70%'}} /></div>; }
export function Pill({children}:{children:ReactNode}){ return <span className="pill">{children}</span>; }
export function VerifiedBadge(){ return <span className="pill" aria-label="Verified">✓ Verified</span>; }
export function Modal({title, onClose, children}:{title:string; onClose:()=>void; children:ReactNode}){ return <div className="modal-scrim" onClick={onClose}><div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={e=>e.stopPropagation()}><div style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}><h2 className="title">{title}</h2><Button onClick={onClose} aria-label="Close dialog">Close</Button></div>{children}</div></div>; }
export function Toast({message}:{message:string}){ return <div className="toast" role="status" aria-live="polite">{message}</div>; }
/** Genuine not-found for a requested resource (deleted post, unknown
 *  profile, out-of-scope verification). It is NOT "the endpoint is not
 *  available yet": after Phase 2 the endpoints exist, so a 404 means the
 *  item is gone. Kept under the old name so existing imports keep working. */
export function ApiUnavailable({endpoint, onRetry}:{endpoint:string; onRetry?:()=>void}){ return <div role="alert" style={{padding:16, border:'1px solid var(--border)', borderRadius:12}}><p className="title">Not found</p><p className="secondary">That item was not found. It may have been removed.{endpoint?<> (<code>{endpoint}</code>)</>:null}</p>{onRetry?<Button onClick={onRetry}>Try again</Button>:null}</div>; }
/** Not-found without an endpoint to name (same meaning as ApiUnavailable). */
export function NotFoundState({title='Not found', body='That item was not found. It may have been removed.', onRetry}:{title?:string; body?:string; onRetry?:()=>void}){ return <div role="alert" style={{padding:16, border:'1px solid var(--border)', borderRadius:12}}><p className="title">{title}</p><p className="secondary">{body}</p>{onRetry?<Button onClick={onRetry}>Try again</Button>:null}</div>; }
/** Reserved for the one case the wording actually fits: an endpoint that
 *  is genuinely unimplemented server-side. No page should render this for
 *  a 404 on an existing endpoint — that is a not-found, not an unavailable
 *  endpoint. Currently no call site uses it (Phase 2 wired the endpoints). */
export function EndpointUnavailable({endpoint, onRetry}:{endpoint:string; onRetry?:()=>void}){ return <div role="alert" style={{padding:16, border:'1px solid var(--border)', borderRadius:12}}><p className="title">Not available yet</p><p className="secondary">The backend endpoint <code>{endpoint}</code> is not implemented yet. No data shown here is real.</p>{onRetry?<Button onClick={onRetry}>Try again</Button>:null}</div>; }
/** Shown once on a login page when the user was sent there by a 401: reads
 *  and clears the flag set by client.ts (SESSION_EXPIRED_KEY). */
export function SessionExpiredNotice(){ const [show]=useState(()=>{ try { if (window.sessionStorage.getItem('unilink:session-expired')) { window.sessionStorage.removeItem('unilink:session-expired'); return true; } } catch { /* storage unavailable */ } return false; }); if (!show) return null; return <p role="alert" className="field-error">Your session has expired. Please log in again.</p>; }
export function PageHeader({eyebrow, title, lead, action}:{eyebrow:string; title:string; lead?:string; action?:ReactNode}){ return <header style={{paddingTop:32}}><p className="eyebrow">{eyebrow}</p><h1 className="display">{title}</h1>{lead?<p className="lead">{lead}</p>:null}{action?<div style={{margin:'16px 0'}}>{action}</div>:null}</header>; }
export function ErrorState({message, onRetry}:{message:string; onRetry?:()=>void}){ return <div role="alert" style={{padding:16}}><p className="title">Something went wrong</p><p className="secondary">{message}</p>{onRetry?<Button onClick={onRetry}>Try again</Button>:null}</div>; }
