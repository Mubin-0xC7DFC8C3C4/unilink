package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="profiles") @Getter @Setter @NoArgsConstructor
public class Profile {
  @Id @Column(name="user_id") private Long id;
  @Column(name="bio") private String bio;
  @Column(name="headline") private String headline;
  @Column(name="cover_key") private String coverKey;
  @Lob
  @Column(name="links") private String links;
  @Column(name="phone_visible") private Boolean phoneVisible;
  @Column(name="created_at") private LocalDateTime createdAt;
  @Column(name="updated_at") private LocalDateTime updatedAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
