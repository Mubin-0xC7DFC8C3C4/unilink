package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="admin_recovery_codes") @Getter @Setter @NoArgsConstructor
public class AdminRecoveryCode {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="admin_id") private Long adminId;
  @Column(name="code_hash") private String codeHash;
  @Column(name="used") private Boolean used;
  @Column(name="used_at") private LocalDateTime usedAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
