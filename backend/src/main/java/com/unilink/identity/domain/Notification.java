package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="notifications") @Getter @Setter @NoArgsConstructor
public class Notification {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="recipient_id") private Long recipientId;
  @Column(name="type") private String type;
  @Column(name="target_type") private String targetType;
  @Column(name="target_id") private Long targetId;
  @Column(name="title") private String title;
  @Column(name="body") private String body;
  @Column(name="idempotency_key") private String idempotencyKey;
  @Column(name="read_flag") private Boolean readFlag;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
