package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="login_attempts") @Getter @Setter @NoArgsConstructor
public class LoginAttempt {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="email", nullable=false) private String email;
  @Column(name="ip") private String ip;
  @Column(name="success") private Boolean success;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
