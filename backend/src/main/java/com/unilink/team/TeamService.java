package com.unilink.team;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.*;
import com.unilink.identity.event.DomainEvents; import com.unilink.identity.repo.*;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
/** TeamFormationFacade (ARCH §8): accept is one transaction — member + room member + notification. */
@Service
public class TeamService {
  private final TeamRepository teams; private final TeamMemberRepository members; private final TeamJoinRequestRepository requests;
  private final PostRepository posts; private final EventDetailRepository events; private final ChatRoomRepository rooms; private final RoomMemberRepository roomMembers;
  private final AccessPolicy policy; private final ApplicationEventPublisher eventsPublisher;
  public TeamService(TeamRepository teams, TeamMemberRepository members, TeamJoinRequestRepository requests,
      PostRepository posts, EventDetailRepository events, ChatRoomRepository rooms, RoomMemberRepository roomMembers,
      AccessPolicy policy, ApplicationEventPublisher eventsPublisher){
    this.teams=teams; this.members=members; this.requests=requests; this.posts=posts; this.events=events; this.rooms=rooms;
    this.roomMembers=roomMembers; this.policy=policy; this.eventsPublisher=eventsPublisher;
  }
  /** Own-scope visibility (D-016/D-021, same matrix as FeedSpecifications.level
   *  and PostService.visibleEvents): NATIONAL is open to every verified student;
   *  UNIVERSITY/DEPARTMENT require the caller's university (and department);
   *  DEPARTMENT_HUB requires the caller's catalog. Out-of-scope is 404 so
   *  existence is not leaked. Note PostService.getPost as written returns any
   *  post to a VERIFIED caller, so it cannot be reused here — the scoped
   *  matrix above is what actually enforces own-scope. */
  private boolean isVisibleTo(Post post, Principals.UnilinkUserPrincipal p){
    return switch (post.getVisibility()){
      case "NATIONAL" -> true;
      case "UNIVERSITY" -> post.getScopeUniversityId()!=null && post.getScopeUniversityId().equals(p.universityId());
      case "DEPARTMENT" -> post.getScopeUniversityId()!=null && post.getScopeUniversityId().equals(p.universityId())
        && post.getScopeDepartmentId()!=null && post.getScopeDepartmentId().equals(p.departmentId());
      case "DEPARTMENT_HUB" -> post.getScopeCatalogId()!=null && post.getScopeCatalogId().equals(p.catalogId());
      default -> false;
    };
  }
  @Transactional
  public TeamDtos.TeamDto createTeam(Principals.UnilinkUserPrincipal p, Long eventPostId, String name, int maxSize){
    policy.requireVerified(p);
    Post post = posts.findById(eventPostId).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (!"EVENT".equals(post.getType())) throw new Exceptions.BadRequest("NOT_AN_EVENT","Teams belong to events");
    if (!isVisibleTo(post, p)) throw new Exceptions.NotFound("Event");
    if (!"ACTIVE".equals(post.getStatus())) throw new Exceptions.BadRequest("POST_NOT_ACTIVE","Event is not active");
    EventDetail ev = events.findById(eventPostId).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (Boolean.TRUE.equals(ev.getRegistrationClosed())) throw new Exceptions.BadRequest("EVENT_CLOSED","Registration for this event is closed");
    if (ev.getArchivedAt()!=null || (ev.getDeadline()!=null && ev.getDeadline().isBefore(LocalDateTime.now())))
      throw new Exceptions.BadRequest("EVENT_PAST","This event has ended");
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
    if (!accept){ r.setStatus("REJECTED"); r.setDecidedAt(java.time.LocalDateTime.now()); requests.save(r); return; }
    // D-145: every check that can fail runs BEFORE the request is marked
    // ACCEPTED. Previously the request was saved ACCEPTED first and the
    // ALREADY_IN_TEAM / missing-room checks threw after it, consuming the
    // request (it could never be re-decided) without the user joining.
    if (members.existsByEventPostIdAndUserId(t.getEventPostId(), r.getUserId()))
      throw new Exceptions.Conflict("ALREADY_IN_TEAM","User already belongs to a team for this event");
    ChatRoom room = rooms.findByTypeAndTeamId("TEAM", t.getId()).orElseThrow(() -> new Exceptions.NotFound("Team chat room"));
    r.setStatus("ACCEPTED"); r.setDecidedAt(java.time.LocalDateTime.now()); requests.save(r);
    TeamMember m = new TeamMember(); m.setTeamId(t.getId()); m.setEventPostId(t.getEventPostId()); m.setUserId(r.getUserId()); members.save(m);
    RoomMember rm = new RoomMember(); rm.setRoomId(room.getId()); rm.setUserId(r.getUserId()); rm.setRole("MEMBER"); roomMembers.save(rm);
    long count = members.countByTeamId(t.getId());
    if (count >= t.getMaxSize()){ t.setStatus("FULL"); teams.save(t); }
    eventsPublisher.publishEvent(new DomainEvents.TeamJoinAccepted(t.getId(), r.getUserId(), t.getEventPostId()));
  }
  /** I-15: teams for an event now require an authenticated caller and the
   *  event to be visible to them (same rule as PostService.getPost: verified
   *  callers see any event, unverified callers NATIONAL only). Previously the
   *  controller took no Authentication at all. */
  @Transactional(readOnly=true)
  public List<TeamDtos.TeamDto> listForEvent(Long eventPostId, Principals.UnilinkUserPrincipal p){
    Post post = posts.findById(eventPostId).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (!"EVENT".equals(post.getType())) throw new Exceptions.NotFound("Event");
    if (!"VERIFIED".equals(p.state()) && !"NATIONAL".equals(post.getVisibility()))
      throw new Exceptions.NotFound("Event"); // D-016
    if ("VERIFIED".equals(p.state()) && !isVisibleTo(post, p)) throw new Exceptions.NotFound("Event");
    return teams.findByEventPostId(eventPostId).stream().map(TeamDtos.TeamDto::from).toList();
  }
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
