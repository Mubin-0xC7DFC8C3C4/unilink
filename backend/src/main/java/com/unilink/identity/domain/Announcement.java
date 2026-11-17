package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="announcements") @Getter @Setter @NoArgsConstructor
public class Announcement {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="admin_id") private Long adminId;
  @Column(name="title") private String title;
  @Lob
  @Column(name="body") private String body;
  @Column(name="scope") private String scope;
  @Column(name="university_id") private Long universityId;
  @Column(name="expires_at") private LocalDateTime expiresAt;
  @Column(name="active") private Boolean active;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
