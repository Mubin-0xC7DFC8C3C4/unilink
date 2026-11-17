package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="verification_requests") @Getter @Setter @NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Version private Long version;
  @Column(name="user_id") private Long userId;
  @Column(name="status") private String status;
  @Column(name="reason") private String reason;
  @Lob
  @Column(name="ocr_text") private String ocrText;
  @Column(name="ocr_match_score") private Double ocrMatchScore;
  @Column(name="ocr_status") private String ocrStatus;
  @Column(name="id_card_key") private String idCardKey;
  @Column(name="profile_photo_key") private String profilePhotoKey;
  @Column(name="submitted_at") private LocalDateTime submittedAt;
  @Column(name="decided_at") private LocalDateTime decidedAt;
  @Column(name="decided_by") private Long decidedBy;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
