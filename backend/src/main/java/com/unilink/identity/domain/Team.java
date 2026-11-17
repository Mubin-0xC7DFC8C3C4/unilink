package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="teams") @Getter @Setter @NoArgsConstructor
@AllArgsConstructor
public class Team {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Version private Long version;
  @Column(name="event_post_id") private Long eventPostId;
  @Column(name="creator_id") private Long creatorId;
  @Column(name="name") private String name;
  @Column(name="max_size") private Integer maxSize;
  @Column(name="status") private String status;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
