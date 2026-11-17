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
  /** D-094: set when a Platform Admin resets this admin's credentials; the
   *  next login is restricted until the password is changed. */
  @Column(name="must_change_password") private Boolean mustChangePassword;
  /** D-095: expiry of a generated temporary password (24 h from issue).
   *  Null for an admin-chosen password. */
  @Column(name="temp_password_expires_at") private LocalDateTime tempPasswordExpiresAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @Column(name="updated_at") private LocalDateTime updatedAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
