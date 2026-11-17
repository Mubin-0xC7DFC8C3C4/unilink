package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="suspension_recommendations") @Getter @Setter @NoArgsConstructor
public class SuspensionRecommendation {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="moderator_id") private Long moderatorId;
  @Column(name="reason") private String reason;
  @Column(name="status") private String status;
  @Column(name="decided_by") private Long decidedBy;
  @Column(name="decided_at") private LocalDateTime decidedAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
