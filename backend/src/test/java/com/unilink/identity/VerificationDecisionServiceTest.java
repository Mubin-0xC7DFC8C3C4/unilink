package com.unilink.identity;

import com.unilink.identity.domain.User;
import com.unilink.identity.domain.VerificationRequest;
import com.unilink.identity.repo.UserRepository;
import com.unilink.identity.repo.VerificationAttemptRepository;
import com.unilink.identity.repo.VerificationRequestRepository;
import com.unilink.identity.service.IdCardMatcher;
import com.unilink.identity.service.VerificationDecisionService;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
import com.unilink.integration.ocr.OcrProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** OCR decision persistence (D-176/D-183): the request row, the account
 *  state and the audit attempt row move together for each outcome. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class VerificationDecisionServiceTest {
  @Autowired VerificationDecisionService decisions;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @Autowired VerificationRequestRepository requests;
  @Autowired VerificationAttemptRepository attempts;
  @BeforeEach void reset(){ TestProviders.reset(); }

  private static String card(String id){
    return """
        METROPOLITAN UNIVERSITY
        STUDENT IDENTITY CARD
        Name: Tanvir Mahmud
        ID: %s
        Department: Computer Science and Engineering
        Address: Dhanmondi, Dhaka
        """.formatted(id);
  }
  private User registered(String tag, String studentId){
    var f = factory.seedMaster();
    User u = factory.verifiedStudent("vd-" + tag + "-" + System.nanoTime() + "@example.com",
        "VD-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    u.setFullName("Tanvir Mahmud"); u.setStudentId(studentId);
    u.setState("REGISTERED"); u.setPhoneVerifiedAt(null); u.setEmailVerifiedAt(null);
    return users.save(u);
  }
  private VerificationRequest requestFor(User u){
    VerificationRequest r = new VerificationRequest();
    r.setUserId(u.getId()); r.setStatus("AWAITING_VERIFICATION"); r.setOcrStatus("PENDING");
    return requests.save(r);
  }
  private IdCardMatcher.Subject subject(User u){
    return new IdCardMatcher.Subject(u.getFullName(), u.getStudentId(), "Metropolitan University",
        "Computer Science and Engineering — MU", "CSE", "Dhanmondi, Dhaka");
  }

  @Test void autoVerifiedLeavesAccountRegisteredForEmailOtp(){
    User u = registered("auto", "232-115-310");
    VerificationRequest r = requestFor(u);
    var out = decisions.applyOcrOutcome(r.getId(), new OcrProvider.Result(card("232-115-310"), 0.95, "DONE", 92.0), subject(u));
    assertEquals(IdCardMatcher.Outcome.AUTO_VERIFIED, out.outcome());
    assertTrue(out.sendEmailOtp());
    var saved = requests.findById(r.getId()).orElseThrow();
    assertEquals("AUTO_VERIFIED", saved.getOcrStatus());
    assertEquals("AWAITING_VERIFICATION", saved.getStatus(), "auto-verified stays out of the admin queue");
    assertEquals("REGISTERED", users.findById(u.getId()).orElseThrow().getState(),
        "activation waits for the email OTP");
    assertEquals(1, attempts.findAll().stream()
        .filter(a -> a.getVerificationRequestId().equals(r.getId()) && "AUTO_VERIFIED".equals(a.getOutcome())).count());
  }
  @Test void wrongCardRejectsAccountAndEnablesResubmit(){
    User u = registered("fail", "232-115-320");
    VerificationRequest r = requestFor(u);
    String wrong = """
        UNIVERSITY OF DHAKA
        STUDENT IDENTITY CARD
        Name: Sakib Hasan
        ID: 999-888-777
        Department: Physics
        DU
        """;
    var out = decisions.applyOcrOutcome(r.getId(), new OcrProvider.Result(wrong, 0.1, "DONE", 92.0), subject(u));
    assertEquals(IdCardMatcher.Outcome.VERIFICATION_FAILED, out.outcome());
    assertFalse(out.sendEmailOtp());
    var saved = requests.findById(r.getId()).orElseThrow();
    assertEquals("VERIFICATION_FAILED", saved.getOcrStatus());
    assertEquals("REJECTED", saved.getStatus());
    assertEquals(IdCardMatcher.REASON_ID, saved.getReason());
    assertEquals("REJECTED", users.findById(u.getId()).orElseThrow().getState(),
        "REJECTED is the state the existing resubmit flow accepts");
  }
  @Test void borderlineGoesToManualReviewInTheAdminQueue(){
    User u = registered("review", "232-115-330");
    VerificationRequest r = requestFor(u);
    String borderline = card("232-115-331").replace("METROPOLITAN UNIVERSITY", "METROPOLITAN UNIV");
    var out = decisions.applyOcrOutcome(r.getId(), new OcrProvider.Result(borderline, 0.7, "DONE", 92.0), subject(u));
    assertEquals(IdCardMatcher.Outcome.MANUAL_REVIEW, out.outcome());
    assertFalse(out.sendEmailOtp());
    var saved = requests.findById(r.getId()).orElseThrow();
    assertEquals("MANUAL_REVIEW", saved.getOcrStatus());
    assertEquals("PENDING_REVIEW", saved.getStatus(), "manual review enters the admin Verification Queue");
    assertEquals("PENDING_REVIEW", users.findById(u.getId()).orElseThrow().getState());
  }
  @Test void ocrFailureBecomesManualReviewNeverStuck(){
    User u = registered("engine", "232-115-340");
    VerificationRequest r = requestFor(u);
    var out = decisions.applyOcrFailure(r.getId(), subject(u));
    assertEquals(IdCardMatcher.Outcome.MANUAL_REVIEW, out.outcome());
    var saved = requests.findById(r.getId()).orElseThrow();
    assertEquals("MANUAL_REVIEW", saved.getOcrStatus());
    assertEquals(IdCardMatcher.REASON_UNCLEAR, saved.getReason());
    assertEquals("PENDING_REVIEW", users.findById(u.getId()).orElseThrow().getState());
  }
}
