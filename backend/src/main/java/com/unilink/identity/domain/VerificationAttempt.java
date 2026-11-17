package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="verification_attempts") @Getter @Setter @NoArgsConstructor
public class VerificationAttempt {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="verification_request_id") private Long verificationRequestId;
  @Column(name="outcome") private String outcome;
  @Column(name="reason") private String reason;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
