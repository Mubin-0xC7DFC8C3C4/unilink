package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="admin_totp_secrets") @Getter @Setter @NoArgsConstructor
public class AdminTotpSecret {
  @Id @Column(name="admin_id") private Long id;
  @Column(name="secret_encrypted") private String secretEncrypted;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
