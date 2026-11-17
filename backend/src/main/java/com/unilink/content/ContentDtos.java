package com.unilink.content;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Content DTOs (ARCH: entities never leave the service layer). Components mirror the entity JSON. */
public final class ContentDtos {
  private ContentDtos(){}
  public record CommentDto(Long id, Long postId, Long authorId, String body, String status, LocalDateTime createdAt){
    public static CommentDto from(Comment c){ return new CommentDto(c.getId(), c.getPostId(), c.getAuthorId(), c.getBody(), c.getStatus(), c.getCreatedAt()); }
  }
  public record BookmarkDto(Long id, Long userId, String targetType, Long targetId, LocalDateTime createdAt){
    public static BookmarkDto from(Bookmark b){ return new BookmarkDto(b.getId(), b.getUserId(), b.getTargetType(), b.getTargetId(), b.getCreatedAt()); }
  }
}
