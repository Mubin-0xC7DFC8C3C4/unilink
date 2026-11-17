package com.unilink.admin;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Admin Portal DTOs (ARCH: entities never leave the service layer).
 *  Record components mirror the entity JSON field names, except secrets and private storage keys,
 *  which are never exposed: AdminAccount.passwordHash, User/VerificationRequest idCardKey/profilePhotoKey. */
public final class AdminDtos {
  private AdminDtos(){}
  public record AdminUserDto(Long id, Long version, Long universityId, Long departmentId, Long batchId,
                             String fullName, String email, String phone, String address, String studentId,
                             String state, LocalDateTime phoneVerifiedAt, LocalDateTime emailVerifiedAt,
                             LocalDateTime createdAt, LocalDateTime updatedAt){
    public static AdminUserDto from(User u){ return new AdminUserDto(u.getId(), u.getVersion(), u.getUniversityId(), u.getDepartmentId(), u.getBatchId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getAddress(), u.getStudentId(), u.getState(), u.getPhoneVerifiedAt(), u.getEmailVerifiedAt(), u.getCreatedAt(), u.getUpdatedAt()); }
  }
  public record VerificationRequestDto(Long id, Long version, Long userId, String status, String reason,
                                       String ocrText, Double ocrMatchScore, String ocrStatus,
                                       LocalDateTime submittedAt, LocalDateTime decidedAt, Long decidedBy,
                                       LocalDateTime createdAt, String fullName, String email,
                                       String university, String studentId, Long previousAttempts,
                                       Boolean duplicatePhone){
    public static VerificationRequestDto from(VerificationRequest r){ return new VerificationRequestDto(r.getId(), r.getVersion(), r.getUserId(), r.getStatus(), r.getReason(), r.getOcrText(), r.getOcrMatchScore(), r.getOcrStatus(), r.getSubmittedAt(), r.getDecidedAt(), r.getDecidedBy(), r.getCreatedAt(), null, null, null, null, null, null); }
    /** Queue enrichment: applicant identity comes from the User row, not the request. */
    public static VerificationRequestDto from(VerificationRequest r, User u, String universityName, long previousAttempts){
      return from(r, u, universityName, previousAttempts, false);
    }
    /** Queue enrichment with the I-17 duplicate-phone flag (D-077): true when
     *  another account shares this applicant's phone number. */
    public static VerificationRequestDto from(VerificationRequest r, User u, String universityName, long previousAttempts, boolean duplicatePhone){
      return new VerificationRequestDto(r.getId(), r.getVersion(), r.getUserId(), r.getStatus(), r.getReason(),
        r.getOcrText(), r.getOcrMatchScore(), r.getOcrStatus(), r.getSubmittedAt(), r.getDecidedAt(), r.getDecidedBy(),
        r.getCreatedAt(), u==null?null:u.getFullName(), u==null?null:u.getEmail(), universityName,
        u==null?null:u.getStudentId(), previousAttempts, duplicatePhone);
    }
  }
  public record IdentityChangeRequestDto(Long id, Long userId, String fullName, String email,
                                         Long newUniversityId, Long newDepartmentId, String newStudentId,
                                         String status, String reason, Long decidedBy,
                                         LocalDateTime createdAt, LocalDateTime decidedAt){
    public static IdentityChangeRequestDto from(IdentityChangeRequest r, User u){
      return new IdentityChangeRequestDto(r.getId(), r.getUserId(), u==null?null:u.getFullName(), u==null?null:u.getEmail(),
        r.getNewUniversityId(), r.getNewDepartmentId(), r.getNewStudentId(), r.getStatus(), r.getReason(),
        r.getDecidedBy(), r.getCreatedAt(), r.getDecidedAt());
    }
  }
  public record AuditLogDto(Long id, Long actorAdminId, String action, String targetType, Long targetId,
                            String reason, String metadata, String ip, String prevHash, String rowHash,
                            LocalDateTime createdAt){
    public static AuditLogDto from(AuditLog l){ return new AuditLogDto(l.getId(), l.getActorAdminId(), l.getAction(), l.getTargetType(), l.getTargetId(), l.getReason(), l.getMetadata(), l.getIp(), l.getPrevHash(), l.getRowHash(), l.getCreatedAt()); }
  }
  public record AdminAlertDto(Long id, Long adminId, Long universityId, String type, String targetType,
                              Long targetId, String message, Boolean readFlag, LocalDateTime createdAt){
    public static AdminAlertDto from(AdminAlert a){ return new AdminAlertDto(a.getId(), a.getAdminId(), a.getUniversityId(), a.getType(), a.getTargetType(), a.getTargetId(), a.getMessage(), a.getReadFlag(), a.getCreatedAt()); }
  }
  public record SuspensionRecommendationDto(Long id, Long userId, Long moderatorId, String reason, String status,
                                            Long decidedBy, LocalDateTime decidedAt, LocalDateTime createdAt){
    public static SuspensionRecommendationDto from(SuspensionRecommendation r){ return new SuspensionRecommendationDto(r.getId(), r.getUserId(), r.getModeratorId(), r.getReason(), r.getStatus(), r.getDecidedBy(), r.getDecidedAt(), r.getCreatedAt()); }
  }
  public record AdminAccountDto(Long id, String email, String role, Long universityId, Boolean active,
                                Boolean totpEnrolled, LocalDateTime createdAt, LocalDateTime updatedAt){
    public static AdminAccountDto from(AdminAccount a){ return new AdminAccountDto(a.getId(), a.getEmail(), a.getRole(), a.getUniversityId(), a.getActive(), a.getTotpEnrolled(), a.getCreatedAt(), a.getUpdatedAt()); }
  }
}
