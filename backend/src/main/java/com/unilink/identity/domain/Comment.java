package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="comments") @Getter @Setter @NoArgsConstructor
public class Comment {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="post_id") private Long postId;
  @Column(name="parent_id") private Long parentId;
  @Column(name="author_id") private Long authorId;
  @Lob
  @Column(name="body") private String body;
  @Column(name="status") private String status;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
