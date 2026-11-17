package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="identity_change_requests") @Getter @Setter @NoArgsConstructor
public class IdentityChangeRequest {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="new_university_id") private Long newUniversityId;
  @Column(name="new_department_id") private Long newDepartmentId;
  @Column(name="new_student_id") private String newStudentId;
  @Column(name="new_id_card_key") private String newIdCardKey;
  @Column(name="status") private String status;
  @Column(name="reason") private String reason;
  @Column(name="decided_at") private LocalDateTime decidedAt;
  @Column(name="decided_by") private Long decidedBy;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
