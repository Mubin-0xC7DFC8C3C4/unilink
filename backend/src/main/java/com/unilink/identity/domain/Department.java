package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="departments") @Getter @Setter @NoArgsConstructor
public class Department {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="university_id") private Long universityId;
  @Column(name="catalog_id") private Long catalogId;
  @Column(name="name") private String name;
  @Column(name="active") private Boolean active;
  @Column(name="created_at") private LocalDateTime createdAt;
  // D-157 (latent): active is NOT NULL DEFAULT 1 (V001); the only writer sets
  // it, so this only guards a future caller that does not.
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(active==null) active=true; }
}
