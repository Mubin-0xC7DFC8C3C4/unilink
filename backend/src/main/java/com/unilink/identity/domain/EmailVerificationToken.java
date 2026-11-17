package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="email_verification_tokens") @Getter @Setter @NoArgsConstructor
public class EmailVerificationToken {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="token_hash") private String tokenHash;
  @Column(name="used") private Boolean used;
  @Column(name="expires_at") private LocalDateTime expiresAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
