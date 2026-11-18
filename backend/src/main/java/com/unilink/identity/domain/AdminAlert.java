package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="admin_alerts") @Getter @Setter @NoArgsConstructor
public class AdminAlert {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="admin_id") private Long adminId;
  @Column(name="university_id") private Long universityId;
  @Column(name="type") private String type;
  @Column(name="target_type") private String targetType;
  @Column(name="target_id") private Long targetId;
  @Column(name="message") private String message;
  @Column(name="read_flag") private Boolean readFlag;
  @Column(name="created_at") private LocalDateTime createdAt;
  // D-157: read_flag is NOT NULL DEFAULT 0 (V003); both writers (AdminService
  // overdue alerts, ModerationService strike alerts) omit it, so the insert
  // sent explicit NULL. The strike path swallowed the failure in a catch.
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(readFlag==null) readFlag=false; }
}
