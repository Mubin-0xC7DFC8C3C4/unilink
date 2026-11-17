package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="room_members") @Getter @Setter @NoArgsConstructor
public class RoomMember {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="room_id") private Long roomId;
  @Column(name="user_id") private Long userId;
  @Column(name="role") private String role;
  @Column(name="muted_until") private LocalDateTime mutedUntil;
  @Column(name="last_read_message_id") private Long lastReadMessageId;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
