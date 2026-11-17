package com.unilink.identity.event;
/** Observer pattern: domain events published in the transaction, listeners fire AFTER_COMMIT (ARCH §8). */
public final class DomainEvents {
  private DomainEvents(){}
  public record StudentVerified(Long userId) {}
  public record StudentRejected(Long userId, String reason) {}
  public record PostAutoHidden(Long postId, Long authorId, String targetType) {}
  public record AppealDecided(Long appealId, Long appellantId, boolean accepted) {}
  public record TeamJoinAccepted(Long teamId, Long userId, Long eventPostId) {}
  public record MessageSent(Long messageId, Long roomId, Long senderId) {}
}
