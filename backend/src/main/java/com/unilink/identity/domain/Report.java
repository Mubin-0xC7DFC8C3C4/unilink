package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="reports") @Getter @Setter @NoArgsConstructor
public class Report {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="reporter_id") private Long reporterId;
  @Column(name="target_type") private String targetType;
  @Column(name="target_id") private Long targetId;
  @Column(name="reason") private String reason;
  @Column(name="status") private String status;
  @Column(name="resolved_at") private LocalDateTime resolvedAt;
  @Column(name="resolved_by") private Long resolvedBy;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
