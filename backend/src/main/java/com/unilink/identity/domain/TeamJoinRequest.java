package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="team_join_requests") @Getter @Setter @NoArgsConstructor
public class TeamJoinRequest {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="team_id") private Long teamId;
  @Column(name="user_id") private Long userId;
  @Column(name="status") private String status;
  @Column(name="message") private String message;
  @Column(name="decided_at") private LocalDateTime decidedAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
