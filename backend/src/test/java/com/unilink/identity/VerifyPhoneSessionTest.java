package com.unilink.identity;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.UserRepository;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Regression (production 2026-11-23): a successful phone-OTP verification
 *  must establish the student session. Registration deliberately creates
 *  no session, and the pages the flow navigates to next (/pending's
 *  GET /auth/verification/status, resubmit) require one — so genuine users
 *  landed on Pending Review with a 401 on the status call. A wrong OTP
 *  must still fail with 400 and create no session. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.session.jdbc.initialize-schema=always",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class VerifyPhoneSessionTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @BeforeEach void reset(){ TestProviders.reset(); }

  private User registeredStudent(String tag){
    var f = factory.seedMaster();
    User u = factory.verifiedStudent("vp-" + tag + "-" + System.nanoTime() + "@example.com",
      "VP-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    u.setState("REGISTERED"); u.setPhoneVerifiedAt(null); u.setEmailVerifiedAt(null);
    return users.save(u);
  }

  @Test void verifyPhoneEstablishesSessionForStatusCall() throws Exception {
    User u = registeredStudent("sess");
    mvc.perform(post("/api/auth/otp/resend").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + "}"))
      .andExpect(status().isOk());
    String otp = TestProviders.SMS_OTPS.get(u.getPhone());
    assertNotNull(otp, "an OTP must have been sent");

    // Wrong code: 400, and no session is established (status stays 401).
    String wrong = "000000".equals(otp) ? "111111" : "000000";
    mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + ",\"otp\":\"" + wrong + "\"}"))
      .andExpect(status().isBadRequest());
    mvc.perform(get("/api/auth/verification/status"))
      .andExpect(status().isUnauthorized());

    // Correct code: 200, phone marked verified, session established
    // (Spring Session is cookie-based — same capture pattern as
    // FullStackIntegrationTest.loginAsStudent).
    var result = mvc.perform(post("/api/auth/verify-phone").contentType(MediaType.APPLICATION_JSON)
        .content("{\"userId\":" + u.getId() + ",\"otp\":\"" + otp + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.phoneVerified").value(true))
      .andReturn();
    assertNotNull(users.findById(u.getId()).orElseThrow().getPhoneVerifiedAt(),
      "phone verification must persist");
    jakarta.servlet.http.Cookie[] cookies = result.getResponse().getCookies();
    assertTrue(java.util.Arrays.stream(cookies).anyMatch(c -> "UL_SESSION".equals(c.getName())),
      "verify-phone must set the UL_SESSION cookie");

    // The follow-on call the /pending page makes now succeeds.
    mvc.perform(get("/api/auth/verification/status").cookie(cookies))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.userId").value(u.getId()));
  }
}
