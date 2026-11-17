package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="achievements") @Getter @Setter @NoArgsConstructor
public class Achievement {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="title") private String title;
  @Lob
  @Column(name="description") private String description;
  @Column(name="year") private Integer year;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
