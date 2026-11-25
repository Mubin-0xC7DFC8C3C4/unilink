package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="universities") @Getter @Setter @NoArgsConstructor
public class University {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="name") private String name;
  @Column(name="short_name") private String shortName;
  /** D-192: UGC category — "PUBLIC" or "PRIVATE" (V015). Null for
   *  legacy rows until the catalogue is classified. */
  @Column(name="category") private String category;
  @Column(name="email_domains") private String emailDomains;
  @Column(name="active") private Boolean active;
  @Column(name="created_at") private LocalDateTime createdAt;
  @Column(name="updated_at") private LocalDateTime updatedAt;
  // D-157: same NULL-to-NOT-NULL class as AdminAccount (V001 updated_at):
  // AdminService.createUniversity sets no updatedAt, so the insert sent explicit NULL.
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=createdAt; }
  @PreUpdate void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
