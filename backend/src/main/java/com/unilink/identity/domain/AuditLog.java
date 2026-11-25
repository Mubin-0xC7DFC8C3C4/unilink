package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="audit_logs") @Getter @Setter @NoArgsConstructor
public class AuditLog {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="actor_admin_id") private Long actorAdminId;
  @Column(name="action") private String action;
  @Column(name="target_type") private String targetType;
  @Column(name="target_id") private Long targetId;
  @Column(name="reason") private String reason;
  // D-170 (2026-11-25): metadata lives in a JSON column (V006) and must NOT
  // be @Lob. Hibernate binds a LOB as a binary-charset parameter and
  // MySQL/TiDB reject it with error 3144 ("Cannot create a JSON value from
  // a string with CHARACTER SET 'binary'") — which broke every account
  // deletion in production (409 on /api/me/delete; the audit INSERT is the
  // first write of that transaction). SqlTypes.JSON binds the value as a
  // UTF-8 string, which the JSON column accepts. The H2 tests never caught
  // this: Hibernate-generated DDL makes the column LONGTEXT there.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name="metadata") private String metadata;
  @Column(name="ip") private String ip;
  @Column(name="prev_hash") private String prevHash;
  @Column(name="row_hash") private String rowHash;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
