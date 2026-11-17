package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="admin_recovery_codes") @Getter @Setter @NoArgsConstructor
public class AdminRecoveryCode {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="admin_id") private Long adminId;
  /** D-096: Argon2id hash of the recovery code (was unsalted SHA-256).
   *  VARCHAR(255): an Argon2id hash is ~97 chars, too long for CHAR(64). */
  @Column(name="code_hash") private String codeHash;
  @Column(name="used") private Boolean used;
  @Column(name="used_at") private LocalDateTime usedAt;
  /** D-096: codes expire 90 days after enrolment (or on re-enrolment, which
   *  deletes all prior codes). Null on pre-V010 rows, which fall back to
   *  createdAt + 90 days when checked. */
  @Column(name="expires_at") private LocalDateTime expiresAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
