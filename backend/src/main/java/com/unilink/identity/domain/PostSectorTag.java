package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="post_sector_tags") @Getter @Setter @NoArgsConstructor
public class PostSectorTag {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="post_id") private Long postId;
  @Column(name="sector_tag_id") private Long sectorTagId;
  @PrePersist void onCreate(){}
}
