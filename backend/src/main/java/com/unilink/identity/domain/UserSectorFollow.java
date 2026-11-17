package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="user_sector_follows") @Getter @Setter @NoArgsConstructor
public class UserSectorFollow {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="sector_tag_id") private Long sectorTagId;
  @PrePersist void onCreate(){}
}
