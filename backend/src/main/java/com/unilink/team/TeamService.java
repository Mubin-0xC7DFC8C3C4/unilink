package com.unilink.team;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.*;
import com.unilink.identity.event.DomainEvents; import com.unilink.identity.repo.*;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** TeamFormationFacade (ARCH §8): accept is one transaction — member + room member + notification. */
@Service
public class TeamService {
  private final TeamRepository teams; private final TeamMemberRepository members; private final TeamJoinRequestRepository requests;
  private final PostRepository posts; private final ChatRoomRepository rooms; private final RoomMemberRepository roomMembers;
  private final AccessPolicy policy; private final ApplicationEventPublisher events;
  public TeamService(TeamRepository teams, TeamMemberRepository members, TeamJoinRequestRepository requests,
      PostRepository posts, ChatRoomRepository rooms, RoomMemberRepository roomMembers,
      AccessPolicy policy, ApplicationEventPublisher events){
    this.teams=teams; this.members=members; this.requests=requests; this.posts=posts; this.rooms=rooms;
    this.roomMembers=roomMembers; this.policy=policy; this.events=events;
  }
  @Transactional
  public TeamDtos.TeamDto createTeam(Principals.UnilinkUserPrincipal p, Long eventPostId, String name, int maxSize){
    policy.requireVerified(p);
    Post post = posts.findById(eventPostId).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (!"EVENT".equals(post.getType())) throw new Exceptions.BadRequest("NOT_AN_EVENT","Teams belong to events");
    if (members.existsByEventPostIdAndUserId(eventPostId, p.userId()))
      throw new Exceptions.Conflict("ALREADY_IN_TEAM","You already belong to a team for this event (D-026)");
    Team t = new Team(); t.setEventPostId(eventPostId); t.setCreatorId(p.userId()); t.setName(name);
    t.setMaxSize(Math.max(2, Math.min(maxSize, 10))); t.setStatus("OPEN");
    t = teams.save(t);
    TeamMember m = new TeamMember(); m.setTeamId(t.getId()); m.setEventPostId(eventPostId); m.setUserId(p.userId()); members.save(m);
    ChatRoom room = new ChatRoom(); room.setPublicId(UUID.randomUUID().toString()); room.setType("TEAM");
    room.setName(name); room.setTeamId(t.getId()); room.setCreatedBy(p.userId());
    room = rooms.save(room);
    RoomMember rm = new RoomMember(); rm.setRoomId(room.getId()); rm.setUserId(p.userId()); rm.setRole("ADMIN"); roomMembers.save(rm);
    return TeamDtos.TeamDto.from(t);
  }
  @Transactional
  public TeamDtos.TeamJoinRequestDto requestJoin(Principals.UnilinkUserPrincipal p, Long teamId, String message){
    policy.requireVerified(p);
    Team t = teams.findById(teamId).orElseThrow(() -> new Exceptions.NotFound("Team"));
    if (!"OPEN".equals(t.getStatus())) throw new Exceptions.BadRequest("TEAM_NOT_OPEN","Team is not accepting members");
    if (members.existsByEventPostIdAndUserId(t.getEventPostId(), p.userId()))
      throw new Exceptions.Conflict("ALREADY_IN_TEAM","You already belong to a team for this event");
    if (requests.existsByTeamIdAndUserIdAndStatus(teamId, p.userId(), "PENDING"))
      throw new Exceptions.Conflict("REQUEST_PENDING","You already have a pending request");
    TeamJoinRequest r = new TeamJoinRequest(); r.setTeamId(teamId); r.setUserId(p.userId());
    r.setStatus("PENDING"); r.setMessage(message); return TeamDtos.TeamJoinRequestDto.from(requests.save(r));
  }
  @Transactional
  public void decideRequest(Principals.UnilinkUserPrincipal p, Long requestId, boolean accept){
    TeamJoinRequest r = requests.findById(requestId).orElseThrow(() -> new Exceptions.NotFound("Join request"));
    Team t = teams.findById(r.getTeamId()).orElseThrow(() -> new Exceptions.NotFound("Team"));
    policy.requireOwnerOr404(p, t.getCreatorId()); // only creator decides (TC-14)
    if (!"PENDING".equals(r.getStatus())) throw new Exceptions.Conflict("ALREADY_DECIDED","Request already decided");
    r.setStatus(accept?"ACCEPTED":"REJECTED"); r.setDecidedAt(java.time.LocalDateTime.now()); requests.save(r);
    if (!accept) return;
    if (members.existsByEventPostIdAndUserId(t.getEventPostId(), r.getUserId()))
      throw new Exceptions.Conflict("ALREADY_IN_TEAM","User already belongs to a team for this event");
    TeamMember m = new TeamMember(); m.setTeamId(t.getId()); m.setEventPostId(t.getEventPostId()); m.setUserId(r.getUserId()); members.save(m);
    ChatRoom room = rooms.findByTypeAndTeamId("TEAM", t.getId()).orElseThrow(() -> new Exceptions.NotFound("Team chat room"));
    RoomMember rm = new RoomMember(); rm.setRoomId(room.getId()); rm.setUserId(r.getUserId()); rm.setRole("MEMBER"); roomMembers.save(rm);
    long count = members.countByTeamId(t.getId());
    if (count >= t.getMaxSize()){ t.setStatus("FULL"); teams.save(t); }
    events.publishEvent(new DomainEvents.TeamJoinAccepted(t.getId(), r.getUserId(), t.getEventPostId()));
  }
  @Transactional(readOnly=true)
  public List<TeamDtos.TeamDto> listForEvent(Long eventPostId){ return teams.findByEventPostId(eventPostId).stream().map(TeamDtos.TeamDto::from).toList(); }
  /** Teams the caller created or joined (drives the Teams page list). */
  @Transactional(readOnly=true)
  public List<TeamDtos.TeamDto> listMyTeams(Principals.UnilinkUserPrincipal p){
    Set<Long> ids = new LinkedHashSet<>();
    teams.findByCreatorId(p.userId()).forEach(t -> ids.add(t.getId()));
    members.findByUserId(p.userId()).forEach(m -> ids.add(m.getTeamId()));
    return teams.findAllById(ids).stream().map(TeamDtos.TeamDto::from).toList();
  }
  @Transactional(readOnly=true)
  public List<TeamDtos.TeamJoinRequestDto> pendingRequests(Long teamId, Principals.UnilinkUserPrincipal p){
    Team t = teams.findById(teamId).orElseThrow(() -> new Exceptions.NotFound("Team"));
    policy.requireOwnerOr404(p, t.getCreatorId());
    return requests.findByTeamIdAndStatusOrderByCreatedAtAsc(teamId, "PENDING").stream().map(TeamDtos.TeamJoinRequestDto::from).toList();
  }
  @Transactional
  public void closeTeamsForEvent(Long eventPostId){
    for (Team t : teams.findByEventPostId(eventPostId)){
      if ("OPEN".equals(t.getStatus()) || "FULL".equals(t.getStatus())){ t.setStatus("CLOSED_EVENT_PAST"); teams.save(t); }
    }
  }
}
