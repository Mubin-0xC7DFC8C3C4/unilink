package com.unilink.identity.dto;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
public final class AuthDtos {
  private AuthDtos(){}
  /** FR-AUTH-01 (full SRS text, as amended 2026-11-25): full name, phone,
   *  email, university, department, batch, address, student ID, ID card
   *  image (front + back, D-190). The profile photo was removed from
   *  sign-up (D-178) and is backend-enforced as absent here (D-197):
   *  no profilePhoto part exists on this form any more, so old direct
   *  API clients can no longer attach one at registration — students
   *  upload it from Settings → Edit Profile after logging in. */
  public static class RegisterForm {
    @NotBlank private String fullName;
    /** Bangladeshi mobile: 11 digits, 01[3-9]xxxxxxxx (FR-AUTH-02; +880 form is not accepted). */
    @NotBlank @Pattern(regexp="^01[3-9]\\d{8}$", message="Phone must be a Bangladeshi mobile number, e.g. 01712345678")
    private String phone;
    @NotBlank @Email private String email;
    @NotNull private Long universityId;
    /** D-193: department is a catalog id OR a free-text label — exactly
     *  one is required (enforced in AuthService.register). The label
     *  covers universities with no verified department rows and students
     *  whose department is not listed. */
    private Long departmentId; private String departmentLabel;
    /** D-194: batch/session is free text ("44", "2022-23", "Fall 2024"),
     *  stored verbatim. The old numeric batchId is gone from registration. */
    private String batch;
    @NotBlank private String address; @NotBlank private String studentId;
    @NotBlank private String password; private MultipartFile idCard; private MultipartFile idCardBack;
    public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public Long getUniversityId(){return universityId;} public void setUniversityId(Long v){universityId=v;}
    public Long getDepartmentId(){return departmentId;} public void setDepartmentId(Long v){departmentId=v;}
    public String getDepartmentLabel(){return departmentLabel;} public void setDepartmentLabel(String v){departmentLabel=v;}
    public String getBatch(){return batch;} public void setBatch(String v){batch=v;}
    public String getAddress(){return address;} public void setAddress(String v){address=v;}
    public String getStudentId(){return studentId;} public void setStudentId(String v){studentId=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public MultipartFile getIdCard(){return idCard;} public void setIdCard(MultipartFile v){idCard=v;} public MultipartFile getIdCardBack(){return idCardBack;} public void setIdCardBack(MultipartFile v){idCardBack=v;}
  }
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record OtpVerifyRequest(@NotNull Long userId, @NotBlank String otp) {}
  /** OTP resend (FR-AUTH-04): the user is identified by id, as in OtpVerifyRequest. */
  public record OtpResendRequest(@NotNull Long userId) {}
  public record EmailResendRequest(@NotNull Long userId) {}
  public record PasswordResetRequest(@NotBlank @Email String email) {}
  public record PasswordResetConfirm(@NotBlank @Email String email, @NotBlank String otp, @NotBlank String newPassword) {}
  public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {}
  public record DeleteAccountRequest(@NotBlank String password) {}
  public record UserDto(Long id, String fullName, String email, String phone, Long universityId, Long departmentId,
                        String departmentLabel, Long batchId, String batchLabel, String studentId, String state,
                        boolean phoneVerified, boolean emailVerified,
                        LocalDateTime createdAt, boolean mustChangePassword, Boolean emailSent,
                        /** D-175: registration token, present only in the register / resubmit response. */ String verificationToken) {}
  /** Resend of the email verification link: either the user id (from the
   *  sign-up flow) or the email address (typed in by the user later). */
  public record EmailVerificationResendRequest(Long userId, String email) {}
  public record VerificationStatusDto(Long userId, String state, String requestStatus, Double ocrMatchScore,
                                      String ocrStatus, String rejectionReason) {}
  /** Registration-status lookup (D-175): scoped by the registration token,
   *  so it carries no user id, email or name — nothing an attacker can
   *  enumerate. emailOtpSent is true while an unused, unexpired email OTP
   *  exists for the registration. */
  public record RegistrationStatusDto(String ocrStatus, String requestStatus, String state, Double ocrMatchScore,
                                      String reason, boolean emailOtpSent, boolean emailVerified) {}
  /** Email OTP (D-174): both calls are scoped by the registration token. */
  public record EmailOtpResendRequest(@NotBlank String token) {}
  public record EmailOtpVerifyRequest(@NotBlank String token, @NotBlank String otp) {}
  public record EmailOtpVerifyResponse(boolean emailVerified, String state) {}
}
