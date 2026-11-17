package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="messages") @Getter @Setter @NoArgsConstructor
public class Message {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="public_id") private String publicId;
  @Column(name="room_id") private Long roomId;
  @Column(name="sender_id") private Long senderId;
  @Lob
  @Column(name="body") private String body;
  @Column(name="idempotency_key") private String idempotencyKey;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
