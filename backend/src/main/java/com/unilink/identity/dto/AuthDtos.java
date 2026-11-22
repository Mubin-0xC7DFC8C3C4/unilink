package com.unilink.identity.dto;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
public final class AuthDtos {
  private AuthDtos(){}
  /** FR-AUTH-01 (full SRS text): full name, phone, email, university, department, batch, address,
   *  student ID, ID card image AND profile photo. */
  public static class RegisterForm {
    @NotBlank private String fullName;
    /** Bangladeshi mobile: 11 digits, 01[3-9]xxxxxxxx (FR-AUTH-02; +880 form is not accepted). */
    @NotBlank @Pattern(regexp="^01[3-9]\\d{8}$", message="Phone must be a Bangladeshi mobile number, e.g. 01712345678")
    private String phone;
    @NotBlank @Email private String email;
    @NotNull private Long universityId; @NotNull private Long departmentId; private Long batchId;
    @NotBlank private String address; @NotBlank private String studentId;
    @NotBlank private String password; private MultipartFile idCard; private MultipartFile idCardBack; private MultipartFile profilePhoto;
    public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public Long getUniversityId(){return universityId;} public void setUniversityId(Long v){universityId=v;}
    public Long getDepartmentId(){return departmentId;} public void setDepartmentId(Long v){departmentId=v;}
    public Long getBatchId(){return batchId;} public void setBatchId(Long v){batchId=v;}
    public String getAddress(){return address;} public void setAddress(String v){address=v;}
    public String getStudentId(){return studentId;} public void setStudentId(String v){studentId=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public MultipartFile getIdCard(){return idCard;} public void setIdCard(MultipartFile v){idCard=v;} public MultipartFile getIdCardBack(){return idCardBack;} public void setIdCardBack(MultipartFile v){idCardBack=v;}
    public MultipartFile getProfilePhoto(){return profilePhoto;} public void setProfilePhoto(MultipartFile v){profilePhoto=v;}
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
                        Long batchId, String studentId, String state, boolean phoneVerified, boolean emailVerified,
                        LocalDateTime createdAt, boolean mustChangePassword) {}
  public record VerificationStatusDto(Long userId, String state, String requestStatus, Double ocrMatchScore,
                                      String ocrStatus, String rejectionReason) {}
}
