package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="moderation_snapshots") @Getter @Setter @NoArgsConstructor
public class ModerationSnapshot {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="report_id") private Long reportId;
  @Column(name="message_id") private Long messageId;
  @Column(name="sender_id") private Long senderId;
  @Lob
  @Column(name="body") private String body;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
