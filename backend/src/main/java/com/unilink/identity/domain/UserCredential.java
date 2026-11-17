package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="user_credentials") @Getter @Setter @NoArgsConstructor
public class UserCredential {
  @Id @Column(name="user_id") private Long id;
  @Column(name="password_hash") private String passwordHash;
  @PrePersist void onCreate(){}
}
