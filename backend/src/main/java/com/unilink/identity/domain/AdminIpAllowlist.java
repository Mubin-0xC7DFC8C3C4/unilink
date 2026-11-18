package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="admin_ip_allowlist") @Getter @Setter @NoArgsConstructor
public class AdminIpAllowlist {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="cidr") private String cidr;
  @Column(name="active") private Boolean active;
  @Column(name="created_at") private LocalDateTime createdAt;
  // D-157 (latent): active is NOT NULL DEFAULT 1 (V003); no JPA writer exists
  // yet (only read by AdminAuthService), so this guards a future creator.
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(active==null) active=true; }
}
