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
  // D-157: updated_at is NOT NULL (V002) and phone_visible is NOT NULL
  // DEFAULT 0; registration sets phoneVisible but no updatedAt, and the
  // ProfileService orElseGet insert paths set neither.
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=createdAt; if(phoneVisible==null) phoneVisible=false; }
  @PreUpdate void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
