import { useState } from 'react'; import { useParams, useNavigate } from 'react-router-dom'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, apiDelete, api, errorMessage } from '../../api/client'; import { Button, EmptyState, ErrorState, PageHeader, Skeleton, ApiUnavailable } from '../../design/primitives';
import type { Post, AttachmentItem } from '../../api/types';
interface Comment { id: string|number; body: string; authorName?: string }
export default function PostDetail(){ const {id}=useParams(); const nav=useNavigate(); const qc=useQueryClient(); const [comment,setComment]=useState(''); const [mention,setMention]=useState(''); const [mentionedIds,setMentionedIds]=useState<number[]>([]);
  const post = useQuery({ queryKey:['post',id], queryFn: ()=>apiGet<Post>(`/posts/${id}`), retry:false });
  const me = useQuery({ queryKey:['me'], queryFn: ()=>apiGet<{id:number}>('/auth/me'), retry:false });
  const isOwner = me.data?.id!=null && post.data?.authorId!=null && Number(me.data.id)===Number(post.data.authorId);
  const comments = useQuery({ queryKey:['post-comments',id], queryFn: ()=>apiGet<Comment[]|{items:Comment[]}>(`/posts/${id}/comments`), retry:false });
  const attachments = useQuery({ queryKey:['post-attachments',id], queryFn: ()=>apiGet<AttachmentItem[]>(`/posts/${id}/attachments`), retry:false });
  const addComment = useMutation({ mutationFn: ()=>apiPost(`/posts/${id}/comments`,{body:comment, mentionUserIds: mentionedIds}), onSuccess: ()=>{ setComment(''); setMentionedIds([]); setMention(''); qc.invalidateQueries({queryKey:['post-comments',id]}); } });
  const react = useMutation({ mutationFn: (type:string)=>apiPost(`/reactions?reaction=${type}`,{targetType:'POST', targetId: Number(id)}) });
  const bookmark = useMutation({ mutationFn: ()=>apiPost('/bookmarks',{targetType:'POST', targetId: Number(id)}) });
  const unbookmark = useMutation({ mutationFn: ()=>api(`/bookmarks`,{method:'DELETE', body: JSON.stringify({targetType:'POST', targetId: Number(id)})}) });
  const [reportReason,setReportReason]=useState('spam');
  const report = useMutation({ mutationFn: ()=>apiPost('/reports',{targetType:'POST', targetId: Number(id), reason: reportReason}) });
  const [appealReason,setAppealReason]=useState('');
  const appeal = useMutation({ mutationFn: ()=>apiPost('/appeals',{targetType:'POST', targetId: Number(id), reason: appealReason}) });
  const [editing,setEditing]=useState(false); const [editTitle,setEditTitle]=useState(''); const [editBody,setEditBody]=useState('');
  const edit = useMutation({ mutationFn: ()=>api(`/posts/${id}`,{method:'PATCH', body: JSON.stringify({title: editTitle||undefined, body: editBody||undefined})}), onSuccess: ()=>{ setEditing(false); qc.invalidateQueries({queryKey:['post',id]}); } });
  const del = useMutation({ mutationFn: ()=>apiDelete(`/posts/${id}`), onSuccess: ()=>nav('/feed') });
  const [file,setFile]=useState<File|null>(null);
  const upload = useMutation({ mutationFn: ()=>{ const fd=new FormData(); fd.append('file', file!); return apiPost(`/posts/${id}/attachments`, fd); }, onSuccess: ()=>{ setFile(null); qc.invalidateQueries({queryKey:['post-attachments',id]}); } });
  const citems = Array.isArray(comments.data)?comments.data:comments.data?.items??[];
  const mentionResults = useQuery({ queryKey:['mention-search',mention], queryFn: ()=>apiGet<{users?:{id:number;fullName?:string}[]}>(`/search?q=${encodeURIComponent(mention)}&type=users`), enabled: mention.length>=2, retry:false });
  // Mention selection: the suggestion list is clickable — picking a user
  // inserts @name into the comment and records the id, so addComment can
  // send it as mentionUserIds (the backend accepts them on CommentBody).
  // Typing @name without picking a suggestion records no id.
  const mentionUsers = mentionResults.data?.users ?? [];
  const pickMention=(u:{id:number;fullName?:string})=>{ const n=u.fullName||''; if(!n) return;
    setComment(c=>c.replace(/@\w*$/, `@${n} `)); setMentionedIds(ids=>ids.includes(u.id)?ids:[...ids,u.id]); setMention(''); };
  const hidden = post.data?.status==='HIDDEN_AUTO' || post.data?.status==='HIDDEN_ADMIN';
  const err = react.error||bookmark.error||unbookmark.error||report.error||appeal.error||edit.error||del.error||upload.error;
  return <div className="container">{post.isPending?<Skeleton/>:post.isError?(post.error as any)?.status===404?<ApiUnavailable endpoint={`/posts/${id}`} onRetry={()=>post.refetch()}/>:<ErrorState message={errorMessage(post.error)} onRetry={()=>post.refetch()}/>:<><PageHeader eyebrow="Post" title={post.data?.title||'Post'} lead={post.data?.body||post.data?.content||''} />
  <div style={{display:'flex',gap:8,flexWrap:'wrap'}}><Button onClick={()=>react.mutate('LIKE')}>Like</Button><Button onClick={()=>bookmark.mutate()} disabled={bookmark.isPending}>{bookmark.isSuccess?'Saved':'Bookmark'}</Button><Button onClick={()=>unbookmark.mutate()} disabled={unbookmark.isPending}>Remove bookmark</Button>
  {isOwner && <><Button onClick={()=>{ setEditTitle(post.data?.title||''); setEditBody(post.data?.body||''); setEditing(true); }}>Edit</Button><Button onClick={()=>{ if(confirm('Delete this post?')) del.mutate(); }} disabled={del.isPending}>Delete</Button></>}</div>
  {err && <p role="alert" className="field-error">{errorMessage(err)}</p>}
  {editing && <section className="card-group" style={{marginTop:16}}><h2 className="title">Edit post</h2>
  <label htmlFor="pe-title">Title</label><input id="pe-title" className="input-search" value={editTitle} onChange={e=>setEditTitle(e.target.value)} style={{width:'100%',marginBottom:8}} />
  <label htmlFor="pe-body">Body</label><textarea id="pe-body" className="input-search" value={editBody} onChange={e=>setEditBody(e.target.value)} rows={3} style={{width:'100%',marginBottom:8}} />
  <div style={{display:'flex',gap:8}}><Button variant="primary" onClick={()=>edit.mutate()} disabled={edit.isPending}>Save changes</Button><Button onClick={()=>setEditing(false)}>Cancel</Button></div></section>}
  <section style={{marginTop:24}}><h2 className="headline">Attachments</h2>
  {attachments.isPending?<Skeleton/>:attachments.isError?<ErrorState message={errorMessage(attachments.error)} onRetry={()=>attachments.refetch()}/>:((attachments.data||[]).length===0?<p className="secondary">No attachments.</p>:<ul className="ruled-list">{(attachments.data||[]).map(a=><li key={a.id}><a href={`/api/posts/${id}/attachments/${a.id}`}>{a.fileName}</a><span className="secondary"> · {a.contentType||''}</span></li>)}</ul>)}
  {isOwner && <div style={{display:'flex',gap:8,marginTop:8,alignItems:'center'}}><input aria-label="Attach a file" type="file" onChange={e=>setFile(e.target.files?.[0]||null)} /><Button onClick={()=>file && upload.mutate()} disabled={!file||upload.isPending}>Upload attachment</Button></div>}</section>
  <section style={{marginTop:24}}><h2 className="headline">Report</h2><div style={{display:'flex',gap:8,alignItems:'center'}}><select aria-label="Report reason" value={reportReason} onChange={e=>setReportReason(e.target.value)} className="input-search"><option value="spam">Spam</option><option value="fake">Fake</option><option value="abuse">Abuse</option><option value="other">Other</option></select><Button onClick={()=>report.mutate()} disabled={report.isPending}>Report this post</Button>{report.isSuccess && <span role="status">Reported.</span>}</div>
  {isOwner && hidden && <div style={{marginTop:16}}><h3 className="title">Appeal hiding</h3><div style={{display:'flex',gap:8,alignItems:'center'}}><input aria-label="Appeal reason" className="input-search" value={appealReason} onChange={e=>setAppealReason(e.target.value)} placeholder="Why should this be restored?" style={{flex:1}} /><Button onClick={()=>appeal.mutate()} disabled={!appealReason.trim()||appeal.isPending}>Appeal</Button>{appeal.isSuccess && <span role="status">Appeal submitted.</span>}</div></div>}</section></>}
  <section style={{marginTop:32}}><h2 className="headline">Comments</h2>
  {comments.isPending?<Skeleton/>:comments.isError?<ErrorState message={errorMessage(comments.error)} onRetry={()=>comments.refetch()}/>:citems.length===0?<EmptyState title="No comments yet" body="Start the discussion."/>:<ul className="ruled-list">{citems.map(c=><li key={c.id}><strong>{c.authorName||'Student'}</strong><p>{c.body}</p></li>)}</ul>}
  <form onSubmit={e=>{e.preventDefault(); if(comment.trim()) addComment.mutate();}} style={{marginTop:16}}>
    <label htmlFor="comment">Add a comment</label>
    <input id="comment" value={comment} onChange={e=>{setComment(e.target.value); const m=e.target.value.match(/@(\w*)$/); setMention(m?m[1]:'');}} className="input-search" style={{width:'100%'}} placeholder="Write a comment — type @ to mention someone" />
    {mention.length>=2 && mentionUsers.length>0 && <ul className="ruled-list" aria-label="Mention suggestions">{mentionUsers.map(u=><li key={u.id}><button type="button" className="btn btn-sm" onClick={()=>pickMention(u)}>@{u.fullName||'Student'}</button></li>)}</ul>}
    {addComment.isError && <p role="alert" className="field-error">{errorMessage(addComment.error)}</p>}
    <div style={{marginTop:8}}><Button type="submit" variant="primary" disabled={!comment.trim()||addComment.isPending}>Comment</Button></div></form></section></div>;
}
