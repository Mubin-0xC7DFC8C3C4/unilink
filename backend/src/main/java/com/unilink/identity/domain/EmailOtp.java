package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

/** Email OTP code (D-174): 6 digits, hashed, single-use, 10-minute expiry.
 *  Mirrors PhoneOtp; the code is sent only after OCR reaches AUTO_VERIFIED. */
@Entity @Table(name="email_otps") @Getter @Setter @NoArgsConstructor
public class EmailOtp {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="otp_hash") private String otpHash;
  @Column(name="attempts") private Integer attempts;
  @Column(name="used") private Boolean used;
  @Column(name="expires_at") private LocalDateTime expiresAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
