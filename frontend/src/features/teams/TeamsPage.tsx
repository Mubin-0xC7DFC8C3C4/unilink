import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'; import { ListPage } from '../shared'; import { RuledList, Button, ErrorState } from '../../design/primitives'; import { apiGet, apiPost, errorMessage } from '../../api/client';
/** The backend decide endpoint takes {accept: boolean} at
 *  POST /api/teams/requests/{id}/decide — not /accept with an empty body.
 *  Pending requests are fetched per team, creator only: the backend 404s
 *  for anyone else (TeamService.pendingRequests requires the creator), so
 *  only teams whose creatorId is the caller are queried. */
function TeamRow({t, isOwner, onJoin, joinPending}: {t:any; isOwner:boolean; onJoin:(id:string|number)=>void; joinPending:boolean}){
  const qc=useQueryClient();
  const reqs = useQuery({ queryKey:['team-requests',t.id], queryFn: ()=>apiGet<any[]>(`/teams/${t.id}/requests`), retry:false, enabled: isOwner });
  const decide = useMutation({ mutationFn: ({rid,accept}:{rid:string|number;accept:boolean})=>apiPost(`/teams/requests/${rid}/decide`,{accept}), onSuccess: ()=>{ qc.invalidateQueries({queryKey:['team-requests',t.id]}); qc.invalidateQueries({queryKey:['teams']}); } });
  const pending = Array.isArray(reqs.data)?reqs.data:[];
  return <li><strong>{t.name||`Team ${t.id}`}</strong><p className="secondary">{t.eventPostId?`Event ${t.eventPostId} · `:''}{t.status||''}{t.maxSize?` · up to ${t.maxSize} members`:''}</p><Button onClick={()=>onJoin(t.id)} disabled={joinPending}>Request to join</Button>{' '}
  {pending.map((r:any)=><span key={r.id} style={{marginRight:8}}><Button onClick={()=>decide.mutate({rid:r.id,accept:true})}>Accept {r.userId}</Button>{' '}<Button onClick={()=>decide.mutate({rid:r.id,accept:false})}>Decline {r.userId}</Button></span>)}
  {decide.isError && <p role="alert" className="field-error">{errorMessage(decide.error)}</p>}</li>;
}
export default function TeamsPage(){ const qc=useQueryClient();
  const me = useQuery({ queryKey:['me'], queryFn: ()=>apiGet<{id:number}>('/auth/me'), retry:false });
  const meId = typeof me.data?.id === 'number' ? me.data.id : null;
  const join = useMutation({ mutationFn: (teamId:string|number)=>apiPost(`/teams/${teamId}/join`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['teams']}) });
  return <ListPage<any> eyebrow="Teams" title="Teams" lead="Teams for competitions and hackathons. Join a team, or accept a join request for a team you lead. Team chat opens from the team room in Messages." queryKey={['teams']} path="/teams" emptyTitle="No teams yet" emptyBody="No teams are listed. Create one from an event page." render={items=><RuledList>{items.map((t:any)=><TeamRow key={t.id} t={t} isOwner={meId!=null && t.creatorId!=null && Number(t.creatorId)===meId} onJoin={(id)=>join.mutate(id)} joinPending={join.isPending} />)}</RuledList>} />;
}
