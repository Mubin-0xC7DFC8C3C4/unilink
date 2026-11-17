package com.unilink.identity;
import com.unilink.identity.dto.AuthDtos.RegisterForm;
import jakarta.validation.*;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
/** Backend phone-format validation (I-6 / FR-AUTH-02): RegisterForm.phone was
 *  @NotBlank only. The @Pattern accepts exactly the Bangladeshi mobile form
 *  01[3-9]xxxxxxxx (11 digits); the +880 form is rejected with a clear message. */
class PhoneValidationTest {
  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
  private RegisterForm form(String phone){
    RegisterForm f = new RegisterForm();
    f.setFullName("Test User"); f.setPhone(phone); f.setEmail("test@example.com");
    f.setUniversityId(1L); f.setDepartmentId(1L); f.setAddress("123 Test Road, Dhaka");
    f.setStudentId("CSE-001"); f.setPassword("Password1");
    return f;
  }
  private boolean phoneValid(String phone){
    return VALIDATOR.validate(form(phone)).stream()
      .noneMatch(v -> "phone".equals(v.getPropertyPath().toString()));
  }
  @Test void acceptsBangladeshiMobiles(){
    assertTrue(phoneValid("01712345678"));
    assertTrue(phoneValid("01812345678"));
    assertTrue(phoneValid("01912345678"));
    assertTrue(phoneValid("01312345678"));
    assertTrue(phoneValid("01512345678"));
  }
  @Test void rejectsNonMobilePrefixes(){
    assertFalse(phoneValid("01234567890"), "012 is not a mobile prefix");
    assertFalse(phoneValid("01112345678"), "011 is not a mobile prefix");
    assertFalse(phoneValid("01012345678"), "010 is not a mobile prefix");
  }
  @Test void rejectsWrongLengthsAndFormats(){
    assertFalse(phoneValid("0171234567"), "10 digits");
    assertFalse(phoneValid("017123456789"), "12 digits");
    assertFalse(phoneValid("+8801712345678"), "+880 form is not accepted");
    assertFalse(phoneValid("01712 345678"), "space");
    assertFalse(phoneValid("01712-345678"), "dash");
    assertFalse(phoneValid(""), "blank");
  }
}
