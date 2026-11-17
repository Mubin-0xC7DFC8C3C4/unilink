package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="education_entries") @Getter @Setter @NoArgsConstructor
public class EducationEntry {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="institution") private String institution;
  @Column(name="degree") private String degree;
  // Quoted: `year` is a reserved word in H2, so unquoted DDL/DML fails under
  // the H2 test database; MySQL renders the same column backticked.
  @Column(name="\"year\"") private Integer year;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
