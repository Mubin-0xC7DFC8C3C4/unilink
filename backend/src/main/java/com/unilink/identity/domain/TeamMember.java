package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="team_members") @Getter @Setter @NoArgsConstructor
public class TeamMember {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="team_id") private Long teamId;
  @Column(name="event_post_id") private Long eventPostId;
  @Column(name="user_id") private Long userId;
  @PrePersist void onCreate(){}
}
