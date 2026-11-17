package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="chat_rooms") @Getter @Setter @NoArgsConstructor
public class ChatRoom {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="public_id") private String publicId;
  @Column(name="type") private String type;
  @Column(name="name") private String name;
  @Column(name="department_id") private Long departmentId;
  @Column(name="university_id") private Long universityId;
  @Column(name="team_id") private Long teamId;
  @Column(name="created_by") private Long createdBy;
  @Column(name="closed_at") private LocalDateTime closedAt; // TEMP rooms only (FR-MSG-08); null = open
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
