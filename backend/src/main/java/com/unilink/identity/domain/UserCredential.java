package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="user_credentials") @Getter @Setter @NoArgsConstructor
public class UserCredential {
  @Id @Column(name="user_id") private Long id;
  @Column(name="password_hash") private String passwordHash;
  /** D-094: set when an admin force-resets this password; the next login is
   *  restricted until the password is changed. */
  @Column(name="must_change_password") private Boolean mustChangePassword;
  /** D-095: expiry of a generated temporary password (24 h from issue).
   *  Null for a user-chosen password. */
  @Column(name="temp_password_expires_at") private LocalDateTime tempPasswordExpiresAt;
  @PrePersist void onCreate(){}
}
