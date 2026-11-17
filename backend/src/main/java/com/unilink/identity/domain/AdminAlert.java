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
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
