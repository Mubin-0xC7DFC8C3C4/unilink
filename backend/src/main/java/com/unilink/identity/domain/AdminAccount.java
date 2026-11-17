package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="admin_accounts") @Getter @Setter @NoArgsConstructor
public class AdminAccount {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="email", nullable=false) private String email;
  @Column(name="password_hash") private String passwordHash;
  @Column(name="role") private String role;
  @Column(name="university_id") private Long universityId;
  @Column(name="active") private Boolean active;
  @Column(name="totp_enrolled") private Boolean totpEnrolled;
  @Column(name="created_at") private LocalDateTime createdAt;
  @Column(name="updated_at") private LocalDateTime updatedAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
