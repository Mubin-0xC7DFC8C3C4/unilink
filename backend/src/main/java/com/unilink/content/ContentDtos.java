package com.unilink.content;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Content DTOs (ARCH: entities never leave the service layer). Components mirror the entity JSON. */
public final class ContentDtos {
  private ContentDtos(){}
  public record CommentDto(Long id, Long postId, Long authorId, Long parentId, String body, String status, LocalDateTime createdAt, String authorName){
    public static CommentDto from(Comment c){ return from(c, null); }
    public static CommentDto from(Comment c, String authorName){ return new CommentDto(c.getId(), c.getPostId(), c.getAuthorId(), c.getParentId(), c.getBody(), c.getStatus(), c.getCreatedAt(), authorName); }
  }
  public record BookmarkDto(Long id, Long userId, String targetType, Long targetId, LocalDateTime createdAt, String title){
    public static BookmarkDto from(Bookmark b){ return from(b, null); }
    public static BookmarkDto from(Bookmark b, String title){ return new BookmarkDto(b.getId(), b.getUserId(), b.getTargetType(), b.getTargetId(), b.getCreatedAt(), title); }
  }
  public record AttachmentDto(Long id, String ownerType, Long ownerId, String fileName, String contentType,
                              Long sizeBytes, LocalDateTime createdAt){
    public static AttachmentDto from(Attachment a){ return new AttachmentDto(a.getId(), a.getOwnerType(), a.getOwnerId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getCreatedAt()); }
  }
}
