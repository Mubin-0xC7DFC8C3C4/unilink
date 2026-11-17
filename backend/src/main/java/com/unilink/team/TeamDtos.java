package com.unilink.team;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Team DTOs (ARCH: entities never leave the service layer). Components mirror the entity JSON. */
public final class TeamDtos {
  private TeamDtos(){}
  public record TeamDto(Long id, Long version, Long eventPostId, Long creatorId, String name, Integer maxSize,
                        String status, LocalDateTime createdAt){
    public static TeamDto from(Team t){ return new TeamDto(t.getId(), t.getVersion(), t.getEventPostId(), t.getCreatorId(), t.getName(), t.getMaxSize(), t.getStatus(), t.getCreatedAt()); }
  }
  public record TeamJoinRequestDto(Long id, Long teamId, Long userId, String status, String message,
                                   LocalDateTime decidedAt, LocalDateTime createdAt){
    public static TeamJoinRequestDto from(TeamJoinRequest r){ return new TeamJoinRequestDto(r.getId(), r.getTeamId(), r.getUserId(), r.getStatus(), r.getMessage(), r.getDecidedAt(), r.getCreatedAt()); }
  }
}
