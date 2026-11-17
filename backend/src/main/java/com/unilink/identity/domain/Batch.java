package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="batches") @Getter @Setter @NoArgsConstructor
public class Batch {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="university_id") private Long universityId;
  @Column(name="department_id") private Long departmentId;
  @Column(name="label") private String label;
  @Column(name="start_year") private Integer startYear;
  @Column(name="active") private Boolean active;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
