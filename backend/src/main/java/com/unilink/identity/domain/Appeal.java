package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="appeals") @Getter @Setter @NoArgsConstructor
public class Appeal {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="appellant_id") private Long appellantId;
  @Column(name="target_type") private String targetType;
  @Column(name="target_id") private Long targetId;
  @Lob
  @Column(name="reason") private String reason;
  @Column(name="status") private String status;
  @Column(name="token_hash") private String tokenHash;
  @Column(name="token_expires_at") private LocalDateTime tokenExpiresAt;
  @Column(name="decided_by") private Long decidedBy;
  @Column(name="decision_reason") private String decisionReason;
  @Column(name="decided_at") private LocalDateTime decidedAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
