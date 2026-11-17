package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="user_skills") @Getter @Setter @NoArgsConstructor
public class UserSkill {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="skill_id") private Long skillId;
  @PrePersist void onCreate(){}
}
