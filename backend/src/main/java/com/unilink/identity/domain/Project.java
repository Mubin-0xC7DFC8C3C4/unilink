package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="projects") @Getter @Setter @NoArgsConstructor
public class Project {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="title") private String title;
  @Lob
  @Column(name="description") private String description;
  @Column(name="url") private String url;
  /** D-203: Pin to Profile — the GitHub repository this project is pinned
   *  to. Both NULL = not pinned; both set = render via LiveRepoViewer. */
  @Column(name="github_owner") private String githubOwner;
  @Column(name="github_repo") private String githubRepo;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
