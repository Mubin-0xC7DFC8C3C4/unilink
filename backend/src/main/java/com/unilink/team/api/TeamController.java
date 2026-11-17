package com.unilink.team.api;
import com.unilink.security.AccessPolicy; import com.unilink.team.TeamDtos; import com.unilink.team.TeamService;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/teams")
public class TeamController {
  private final TeamService teams; private final AccessPolicy policy;
  public TeamController(TeamService t, AccessPolicy p){ teams=t; policy=p; }
  public record CreateTeam(Long eventPostId, String name, int maxSize) {}
  public record JoinBody(String message) {}
  public record DecideBody(boolean accept) {}
  @PostMapping public TeamDtos.TeamDto create(Authentication a, @RequestBody CreateTeam r){
    var p = policy.requireStudent(a.getPrincipal());
    return teams.createTeam(p, r.eventPostId(), r.name(), r.maxSize());
  }
  @GetMapping("/event/{eventPostId}") public List<TeamDtos.TeamDto> forEvent(@PathVariable Long eventPostId){ return teams.listForEvent(eventPostId); }
  @PostMapping("/{teamId}/join") public TeamDtos.TeamJoinRequestDto join(Authentication a, @PathVariable Long teamId, @RequestBody(required=false) JoinBody r){
    var p = policy.requireStudent(a.getPrincipal());
    return teams.requestJoin(p, teamId, r==null?null:r.message());
  }
  @GetMapping("/{teamId}/requests") public List<TeamDtos.TeamJoinRequestDto> pending(Authentication a, @PathVariable Long teamId){
    var p = policy.requireStudent(a.getPrincipal()); return teams.pendingRequests(teamId, p);
  }
  @PostMapping("/requests/{requestId}/decide") public Map<String,String> decide(Authentication a, @PathVariable Long requestId, @RequestBody DecideBody r){
    var p = policy.requireStudent(a.getPrincipal()); teams.decideRequest(p, requestId, r.accept());
    return Map.of("status", r.accept()?"accepted":"rejected");
  }
}
