package com.unilink.admin;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Admin Portal DTOs (ARCH: entities never leave the service layer).
 *  Record components mirror the entity JSON field names, except secrets and private storage keys,
 *  which are never exposed: AdminAccount.passwordHash, User/VerificationRequest idCardKey/profilePhotoKey. */
public final class AdminDtos {
  private AdminDtos(){}
  public record AdminUserDto(Long id, Long version, Long universityId, Long departmentId, String departmentLabel,
                             Long batchId, String batchLabel,
                             String fullName, String email, String phone, String address, String studentId,
                             String state, LocalDateTime phoneVerifiedAt, LocalDateTime emailVerifiedAt,
                             LocalDateTime createdAt, LocalDateTime updatedAt){
    public static AdminUserDto from(User u){ return new AdminUserDto(u.getId(), u.getVersion(), u.getUniversityId(), u.getDepartmentId(), u.getDepartmentLabel(), u.getBatchId(), u.getBatchLabel(), u.getFullName(), u.getEmail(), u.getPhone(), u.getAddress(), u.getStudentId(), u.getState(), u.getPhoneVerifiedAt(), u.getEmailVerifiedAt(), u.getCreatedAt(), u.getUpdatedAt()); }
  }
  /** D-203/D-204 (A): phoneVerifiedAt/emailVerifiedAt are surfaced so a
   *  reviewer can see which half of FR-AUTH-07 is done without opening the
   *  student record; addressConflict is the Slice A evidence flag (D-196),
   *  recomputed from the stored OCR texts at queue time (display-only). */
  public record VerificationRequestDto(Long id, Long version, Long userId, String status, String reason,
                                       String ocrText, String ocrTextBack, Double ocrMatchScore, String ocrStatus,
                                       LocalDateTime submittedAt, LocalDateTime decidedAt, Long decidedBy,
                                       LocalDateTime createdAt, String fullName, String email,
                                       String university, String studentId, Long previousAttempts,
                                       Boolean duplicatePhone, LocalDateTime phoneVerifiedAt,
                                       LocalDateTime emailVerifiedAt, Boolean addressConflict){
    public static VerificationRequestDto from(VerificationRequest r){ return new VerificationRequestDto(r.getId(), r.getVersion(), r.getUserId(), r.getStatus(), r.getReason(), r.getOcrText(), r.getOcrTextBack(), r.getOcrMatchScore(), r.getOcrStatus(), r.getSubmittedAt(), r.getDecidedAt(), r.getDecidedBy(), r.getCreatedAt(), null, null, null, null, null, null, null, null, null); }
    /** Queue enrichment: applicant identity comes from the User row, not the request. */
    public static VerificationRequestDto from(VerificationRequest r, User u, String universityName, long previousAttempts){
      return from(r, u, universityName, previousAttempts, false);
    }
    /** Queue enrichment with the I-17 duplicate-phone flag (D-077): true when
     *  another account shares this applicant's phone number. */
    public static VerificationRequestDto from(VerificationRequest r, User u, String universityName, long previousAttempts, boolean duplicatePhone){
      return from(r, u, universityName, previousAttempts, duplicatePhone, false);
    }
    /** Queue enrichment with the address-evidence flag (D-196/D-203). */
    public static VerificationRequestDto from(VerificationRequest r, User u, String universityName, long previousAttempts, boolean duplicatePhone, boolean addressConflict){
      return new VerificationRequestDto(r.getId(), r.getVersion(), r.getUserId(), r.getStatus(), r.getReason(),
        r.getOcrText(), r.getOcrTextBack(), r.getOcrMatchScore(), r.getOcrStatus(), r.getSubmittedAt(), r.getDecidedAt(), r.getDecidedBy(),
        r.getCreatedAt(), u==null?null:u.getFullName(), u==null?null:u.getEmail(), universityName,
        u==null?null:u.getStudentId(), previousAttempts, duplicatePhone,
        u==null?null:u.getPhoneVerifiedAt(), u==null?null:u.getEmailVerifiedAt(), addressConflict);
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
  /** D-204 (B): one login-attempt row. Email, IP, success and timestamp
   *  only — never passwords, OTPs or tokens (login_attempts carries none). */
  public record LoginAttemptDto(Long id, String email, String ip, Boolean success, LocalDateTime createdAt){
    public static LoginAttemptDto from(LoginAttempt a){ return new LoginAttemptDto(a.getId(), a.getEmail(), a.getIp(), a.getSuccess(), a.getCreatedAt()); }
  }
  /** D-204 (C): one row of the all-posts view — author, content, creation
   *  time, moderation status and the count of ACTIVE reports against it
   *  (unreported posts carry 0 and are listed too). */
  public record AdminPostDto(Long id, Long authorId, String authorName, String authorEmail, String type,
                             String visibility, String title, String body, String status,
                             LocalDateTime createdAt, long reportCount){}
  /** D-204: paginated admin list envelope (page is 0-based). */
  public record PageResult<T>(java.util.List<T> content, int page, int size, long totalElements, int totalPages){}
  /** D-204 (D): per-user activity — audit rows targeting the user plus
   *  their login attempts (matched on the account email). */
  public record UserActivityDto(AdminUserDto user, java.util.List<AuditLogDto> auditLogs,
                                java.util.List<LoginAttemptDto> loginAttempts){}
  /** D-204 (F): audit chain verification as surfaced to admins. */
  public record AuditVerifyDto(boolean ok, Long brokenRowId, long totalRows, long verifiedRows, long legacyRows){}
}
