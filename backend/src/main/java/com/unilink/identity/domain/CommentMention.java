package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="comment_mentions") @Getter @Setter @NoArgsConstructor
public class CommentMention {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="comment_id") private Long commentId;
  @Column(name="mentioned_user_id") private Long mentionedUserId;
  @PrePersist void onCreate(){}
}
