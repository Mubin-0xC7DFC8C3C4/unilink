package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="password_reset_tokens") @Getter @Setter @NoArgsConstructor
public class PasswordResetToken {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="otp_hash") private String otpHash;
  @Column(name="attempts") private Integer attempts;
  @Column(name="used") private Boolean used;
  @Column(name="expires_at") private LocalDateTime expiresAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
