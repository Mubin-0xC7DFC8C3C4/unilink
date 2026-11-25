package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
@AllArgsConstructor
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Version private Long version;
  @Column(name="university_id") private Long universityId;
  @Column(name="department_id") private Long departmentId;
  /** D-193: free-text department for universities with no verified
   *  department rows (or a student whose department is not listed).
   *  Mutually exclusive with departmentId: exactly one is set. */
  @Column(name="department_label") private String departmentLabel;
  @Column(name="batch_id") private Long batchId;
  /** D-194: free-text batch/session, stored verbatim ("44", "2022-23",
   *  "Fall 2024"). New registrations set this and leave batchId null;
   *  legacy rows keep batchId and resolve the label through the table. */
  @Column(name="batch_label") private String batchLabel;
  @Column(name="full_name", nullable=false) private String fullName;
  @Column(name="email", nullable=false) private String email;
  @Column(name="phone") private String phone;
  @Column(name="address") private String address;
  @Column(name="student_id") private String studentId;
  @Column(name="state") private String state;
  @Column(name="phone_verified_at") private LocalDateTime phoneVerifiedAt;
  @Column(name="email_verified_at") private LocalDateTime emailVerifiedAt;
  @Column(name="profile_photo_key") private String profilePhotoKey;
  @Column(name="id_card_key") private String idCardKey;
  @Column(name="id_card_back_key") private String idCardBackKey;
  @Column(name="created_at") private LocalDateTime createdAt;
  @Column(name="updated_at") private LocalDateTime updatedAt;
  // D-157: same NULL-to-NOT-NULL class as AdminAccount (V002 updated_at):
  // AuthService.register sets no updatedAt, so the insert sent explicit NULL.
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=createdAt; }
  @PreUpdate void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
