package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="audit_logs") @Getter @Setter @NoArgsConstructor
public class AuditLog {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="actor_admin_id") private Long actorAdminId;
  @Column(name="action") private String action;
  @Column(name="target_type") private String targetType;
  @Column(name="target_id") private Long targetId;
  @Column(name="reason") private String reason;
  @Lob
  @Column(name="metadata") private String metadata;
  @Column(name="ip") private String ip;
  @Column(name="prev_hash") private String prevHash;
  @Column(name="row_hash") private String rowHash;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
