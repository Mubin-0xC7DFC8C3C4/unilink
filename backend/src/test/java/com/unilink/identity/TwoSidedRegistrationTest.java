package com.unilink.identity;

import com.unilink.identity.repo.UserRepository;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
import com.unilink.security.LoginThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Two-image registration contract (D-190/D-193/D-194): the back image
 *  is required, byte-identical front/back is rejected, the batch is
 *  free text validated by the same rule on both sides, and a free-text
 *  department is stored verbatim with no catalog row. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "spring.session.jdbc.initialize-schema=always"
})
class TwoSidedRegistrationTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @Autowired LoginThrottle throttle;
  @BeforeEach void reset(){ TestProviders.reset(); throttle.resetForTests(); }

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};
  private static final byte[] PNG_BACK = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 9, 8, 7, 6};

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder baseForm(
      String email, String studentId, String batch) throws Exception {
    var f = factory.seedMaster();
    return multipart("/api/auth/register")
        .file(new MockMultipartFile("idCard", "id.jpg", "image/jpeg", PNG))
        .file(new MockMultipartFile("idCardBack", "id-back.jpg", "image/jpeg", PNG_BACK))
        .param("fullName", "Tanvir Mahmud").param("phone", "01712345678").param("email", email)
        .param("universityId", f.mu().getId().toString()).param("departmentId", f.muCse().getId().toString())
        .param("batch", batch)
        .param("address", "Bateshwar, Sylhet-3104")
        .param("studentId", studentId).param("password", "Password1");
  }

  @Test void missingBackImageIsRejected() throws Exception {
    var f = factory.seedMaster();
    mvc.perform(multipart("/api/auth/register")
        .file(new MockMultipartFile("idCard", "id.jpg", "image/jpeg", PNG))
        .param("fullName", "Tanvir Mahmud").param("phone", "01712345678")
        .param("email", "noback-" + System.nanoTime() + "@example.com")
        .param("universityId", f.mu().getId().toString()).param("departmentId", f.muCse().getId().toString())
        .param("batch", "44").param("address", "Bateshwar, Sylhet-3104")
        .param("studentId", "NB-" + System.nanoTime()).param("password", "Password1"))
      .andExpect(status().isBadRequest())
      .andExpect(content().string(org.hamcrest.Matchers.containsString("ID_CARD_BACK_REQUIRED")));
  }
  @Test void sameImageAsBothSidesIsRejected() throws Exception {
    var f = factory.seedMaster();
    mvc.perform(multipart("/api/auth/register")
        .file(new MockMultipartFile("idCard", "id.jpg", "image/jpeg", PNG))
        .file(new MockMultipartFile("idCardBack", "id-back.jpg", "image/jpeg", PNG))
        .param("fullName", "Tanvir Mahmud").param("phone", "01712345678")
        .param("email", "sameimg-" + System.nanoTime() + "@example.com")
        .param("universityId", f.mu().getId().toString()).param("departmentId", f.muCse().getId().toString())
        .param("batch", "44").param("address", "Bateshwar, Sylhet-3104")
        .param("studentId", "SI-" + System.nanoTime()).param("password", "Password1"))
      .andExpect(status().isBadRequest())
      .andExpect(content().string(org.hamcrest.Matchers.containsString("ID_CARD_SAME_IMAGE")));
  }
  @Test void invalidBatchIsRejected() throws Exception {
    String email = "badbatch-" + System.nanoTime() + "@example.com";
    mvc.perform(baseForm(email, "BB-" + System.nanoTime(), "Fall 2024!"))
      .andExpect(status().isBadRequest())
      .andExpect(content().string(org.hamcrest.Matchers.containsString("INVALID_BATCH")));
    mvc.perform(baseForm("badbatch2-" + System.nanoTime() + "@example.com", "BB2-" + System.nanoTime(),
        "a".repeat(33)))
      .andExpect(status().isBadRequest())
      .andExpect(content().string(org.hamcrest.Matchers.containsString("INVALID_BATCH")));
  }
  @Test void freeTextBatchFormatsAreAccepted() throws Exception {
    for (String batch : new String[]{"44", "45", "2022-23", "Fall 2024", "Spring 2025", "2023", "59th Batch"}){
      String email = "batch-" + batch.replaceAll("[^A-Za-z0-9]", "") + "-" + System.nanoTime() + "@example.com";
      mvc.perform(baseForm(email, "FB-" + System.nanoTime(), batch))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.batchLabel").value(batch));
      assertEquals(batch, users.findByEmail(email).orElseThrow().getBatchLabel(),
          "the batch must be stored verbatim for " + batch);
      assertNull(users.findByEmail(email).orElseThrow().getBatchId(),
          "a free-text batch must not invent a batch row for " + batch);
    }
  }
  @Test void freeTextDepartmentIsStoredWithoutACatalogRow() throws Exception {
    var f = factory.seedMaster();
    String email = "freedept-" + System.nanoTime() + "@example.com";
    mvc.perform(multipart("/api/auth/register")
        .file(new MockMultipartFile("idCard", "id.jpg", "image/jpeg", PNG))
        .file(new MockMultipartFile("idCardBack", "id-back.jpg", "image/jpeg", PNG_BACK))
        .param("fullName", "Tanvir Mahmud").param("phone", "01712345678").param("email", email)
        .param("universityId", f.mu().getId().toString())
        .param("departmentLabel", "Robotics and Mechatronics")
        .param("batch", "Fall 2024")
        .param("address", "Bateshwar, Sylhet-3104")
        .param("studentId", "FD-" + System.nanoTime()).param("password", "Password1"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.departmentLabel").value("Robotics and Mechatronics"))
      .andExpect(jsonPath("$.batchLabel").value("Fall 2024"));
    var u = users.findByEmail(email).orElseThrow();
    assertNull(u.getDepartmentId());
    assertEquals("Robotics and Mechatronics", u.getDepartmentLabel());
    assertEquals("Fall 2024", u.getBatchLabel());
  }
  @Test void missingDepartmentEntirelyIsRejected() throws Exception {
    var f = factory.seedMaster();
    mvc.perform(multipart("/api/auth/register")
        .file(new MockMultipartFile("idCard", "id.jpg", "image/jpeg", PNG))
        .file(new MockMultipartFile("idCardBack", "id-back.jpg", "image/jpeg", PNG_BACK))
        .param("fullName", "Tanvir Mahmud").param("phone", "01712345678")
        .param("email", "nodept-" + System.nanoTime() + "@example.com")
        .param("universityId", f.mu().getId().toString())
        .param("batch", "44").param("address", "Bateshwar, Sylhet-3104")
        .param("studentId", "ND-" + System.nanoTime()).param("password", "Password1"))
      .andExpect(status().isBadRequest())
      .andExpect(content().string(org.hamcrest.Matchers.containsString("DEPARTMENT_REQUIRED")));
  }
}
