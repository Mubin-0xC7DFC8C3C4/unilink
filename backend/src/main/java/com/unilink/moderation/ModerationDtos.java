package com.unilink.moderation;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Moderation DTOs (ARCH: entities never leave the service layer).
 *  Record components mirror the entity JSON field names; Appeal.tokenHash (hash of the single-use
 *  appeal link token) is never exposed. */
public final class ModerationDtos {
  private ModerationDtos(){}
  public record ReportDto(Long id, Long reporterId, String targetType, Long targetId, String reason,
                          String status, LocalDateTime resolvedAt, Long resolvedBy, LocalDateTime createdAt){
    public static ReportDto from(Report r){ return new ReportDto(r.getId(), r.getReporterId(), r.getTargetType(), r.getTargetId(), r.getReason(), r.getStatus(), r.getResolvedAt(), r.getResolvedBy(), r.getCreatedAt()); }
  }
  public record AppealDto(Long id, Long appellantId, String targetType, Long targetId, String reason,
                          String status, LocalDateTime tokenExpiresAt, Long decidedBy, String decisionReason,
                          LocalDateTime decidedAt, LocalDateTime createdAt){
    public static AppealDto from(Appeal a){ return new AppealDto(a.getId(), a.getAppellantId(), a.getTargetType(), a.getTargetId(), a.getReason(), a.getStatus(), a.getTokenExpiresAt(), a.getDecidedBy(), a.getDecisionReason(), a.getDecidedAt(), a.getCreatedAt()); }
  }
}
