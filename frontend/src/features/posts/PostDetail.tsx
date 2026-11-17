import { useState } from 'react'; import { useParams } from 'react-router-dom'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client'; import { Button, EmptyState, ErrorState, PageHeader, Skeleton, ApiUnavailable } from '../../design/primitives';
interface Comment { id: string|number; body: string; authorName?: string }
export default function PostDetail(){ const {id}=useParams(); const qc=useQueryClient(); const [comment,setComment]=useState(''); const [mention,setMention]=useState('');
  const post = useQuery({ queryKey:['post',id], queryFn: ()=>apiGet<any>(`/posts/${id}`), retry:false });
  const comments = useQuery({ queryKey:['post-comments',id], queryFn: ()=>apiGet<Comment[]|{items:Comment[]}>(`/posts/${id}/comments`), retry:false });
  const addComment = useMutation({ mutationFn: ()=>apiPost(`/posts/${id}/comments`,{body:comment}), onSuccess: ()=>{ setComment(''); qc.invalidateQueries({queryKey:['post-comments',id]}); } });
  const react = useMutation({ mutationFn: (type:string)=>apiPost(`/posts/${id}/reactions`,{type}) });
  const bookmark = useMutation({ mutationFn: ()=>apiPost('/bookmarks',{targetType:'POST', targetId:id}) });
  const citems = Array.isArray(comments.data)?comments.data:comments.data?.items??[];
  const suggestions = ['@teammate','@moderator'];
  return <div className="container">{post.isPending?<Skeleton/>:post.isError?(post.error as any)?.status===404?<ApiUnavailable endpoint={`/posts/${id}`}/>:<ErrorState message={errorMessage(post.error)}/>:<><PageHeader eyebrow="Post" title={post.data?.title||'Post'} lead={post.data?.body||post.data?.content||''} />
  <div style={{display:'flex',gap:8}}><Button onClick={()=>react.mutate('LIKE')}>Like</Button><Button onClick={()=>bookmark.mutate()} disabled={bookmark.isPending}>{bookmark.isSuccess?'Saved':'Bookmark'}</Button></div>
  {(react.isError||bookmark.isError) && <p role="alert" className="field-error">{errorMessage(react.error||bookmark.error)}</p>}</>}
  <section style={{marginTop:32}}><h2 className="headline">Comments</h2>
  {comments.isPending?<Skeleton/>:comments.isError?<ErrorState message={errorMessage(comments.error)}/>:citems.length===0?<EmptyState title="No comments yet" body="Start the discussion."/>:<ul className="ruled-list">{citems.map(c=><li key={c.id}><strong>{c.authorName||'Student'}</strong><p>{c.body}</p></li>)}</ul>}
  <form onSubmit={e=>{e.preventDefault(); if(comment.trim()) addComment.mutate();}} style={{marginTop:16}}>
    <label htmlFor="comment">Add a comment</label>
    <input id="comment" list="mentions" value={comment} onChange={e=>{setComment(e.target.value); const m=e.target.value.match(/@(\w*)$/); setMention(m?m[1]:'');}} className="input-search" style={{width:'100%'}} placeholder="Write a comment — type @ to mention someone" />
    <datalist id="mentions">{mention!=='' && suggestions.filter(s=>s.includes(mention)).map(s=><option key={s} value={s} />)}</datalist>
    {addComment.isError && <p role="alert" className="field-error">{errorMessage(addComment.error)}</p>}
    <div style={{marginTop:8}}><Button type="submit" variant="primary" disabled={!comment.trim()||addComment.isPending}>Comment</Button></div></form></section></div>;
}
