package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="skills") @Getter @Setter @NoArgsConstructor
public class Skill {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="name") private String name;
  @Column(name="active") private Boolean active;
  @Column(name="created_at") private LocalDateTime createdAt;
  // D-157: active is NOT NULL DEFAULT 1 (V001); ProfileService.addSkill builds
  // a Skill with name only, so the insert sent explicit NULL.
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(active==null) active=true; }
}
