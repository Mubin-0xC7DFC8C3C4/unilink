package com.unilink.identity.domain;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;

/** UniLink-native project discussion comment (NOT a GitHub Issue).
 *  Replies are rows with parentId set; fileRef is an optional @file path. */
@Entity @Table(name="project_discussions") @Getter @Setter @NoArgsConstructor
public class ProjectDiscussion {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="repo_full_name") private String repoFullName;
  @Column(name="author_id") private Long authorId;
  @Column(name="parent_id") private Long parentId;
  @Column(name="body") private String body;
  @Column(name="file_ref") private String fileRef;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
