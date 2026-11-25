package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="department_catalog") @Getter @Setter @NoArgsConstructor
public class DepartmentCatalog {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="name") private String name;
  @Column(name="short_name") private String shortName;
  @Column(name="active") private Boolean active;
  @Column(name="created_at") private LocalDateTime createdAt;
  // D-198: provenance — the confirming official source for this canonical
  // discipline, when one has been recorded. Left NULL by the V016 seed
  // (the names come from the brief's list, which is not a URL); a
  // Platform Admin fills these when verifying a row through master data.
  @Column(name="source_url") private String sourceUrl;
  @Column(name="verified_at") private LocalDateTime verifiedAt;
  @Column(name="verified_by") private String verifiedBy;
  // D-157 (latent): active is NOT NULL DEFAULT 1 (V001); no JPA writer exists
  // yet, so this only guards a future caller that builds one without active.
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(active==null) active=true; }
}
