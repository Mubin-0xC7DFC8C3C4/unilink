package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

/** D-198: alternate spellings/abbreviations for a canonical department
 *  (e.g. "CSE" for Computer Science and Engineering). The alias is
 *  globally unique (V016 uk_dept_alias), so one alias maps to exactly
 *  one canonical row. */
@Entity @Table(name="department_aliases") @Getter @Setter @NoArgsConstructor
public class DepartmentAlias {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="catalog_id") private Long catalogId;
  @Column(name="alias") private String alias;
  @Column(name="active") private Boolean active;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(active==null) active=true; }
}
