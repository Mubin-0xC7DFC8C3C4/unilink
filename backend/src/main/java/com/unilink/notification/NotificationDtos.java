package com.unilink.notification;
import com.unilink.identity.domain.Notification;
import java.time.LocalDateTime;
/** Notification DTOs (ARCH: entities never leave the service layer). Components mirror the entity JSON. */
public final class NotificationDtos {
  private NotificationDtos(){}
  public record NotificationDto(Long id, Long recipientId, String type, String targetType, Long targetId,
                                String title, String body, String idempotencyKey, Boolean readFlag,
                                LocalDateTime createdAt){
    public static NotificationDto from(Notification n){ return new NotificationDto(n.getId(), n.getRecipientId(), n.getType(), n.getTargetType(), n.getTargetId(), n.getTitle(), n.getBody(), n.getIdempotencyKey(), n.getReadFlag(), n.getCreatedAt()); }
  }
}
