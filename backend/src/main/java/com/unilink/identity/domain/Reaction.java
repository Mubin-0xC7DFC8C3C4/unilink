package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="reactions") @Getter @Setter @NoArgsConstructor
public class Reaction {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="target_type") private String targetType;
  @Column(name="target_id") private Long targetId;
  @Column(name="reaction") private String reaction;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
