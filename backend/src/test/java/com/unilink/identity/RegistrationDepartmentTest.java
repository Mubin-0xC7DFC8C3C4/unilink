package com.unilink.identity;

import com.unilink.identity.domain.Department;
import com.unilink.identity.domain.DepartmentCatalog;
import com.unilink.identity.repo.DepartmentCatalogRepository;
import com.unilink.identity.repo.DepartmentRepository;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Department choice at registration (D-198/D-193): a departments row
 *  linked to a newly-added catalog entry registers exactly like the
 *  seeded CSE row (label null); DEPARTMENT_MISMATCH is still enforced
 *  across universities; and the typed label still works for a university
 *  with no verified link row, storing the label verbatim with no
 *  departments row invented. */
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
class RegistrationDepartmentTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @Autowired UserRepository users;
  @Autowired DepartmentCatalogRepository catalogs;
  @Autowired DepartmentRepository departments;
  @Autowired LoginThrottle throttle;
  @BeforeEach void reset(){ TestProviders.reset(); throttle.resetForTests(); }

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};
  private static final byte[] PNG_BACK = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 9, 8, 7, 6};

  /** A departments row for a catalog entry that V001 never linked: the
   *  admin master-data flow (createDepartment) is how such a row appears;
   *  the test builds the same row directly. */
  private Department muSoftwareEngineering(){
    var f = factory.seedMaster();
    DepartmentCatalog swe = catalogs.findByName("Software Engineering").orElseGet(() -> {
      DepartmentCatalog c = new DepartmentCatalog(); c.setName("Software Engineering"); c.setShortName("SWE"); c.setActive(true);
      return catalogs.save(c);
    });
    return departments.findByUniversityIdAndCatalogId(f.mu().getId(), swe.getId()).orElseGet(() -> {
      Department d = new Department(); d.setUniversityId(f.mu().getId()); d.setCatalogId(swe.getId());
      d.setName("Software Engineering — MU"); d.setActive(true); return departments.save(d);
    });
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder baseForm(
      String email, String studentId, Long universityId, Long departmentId, String departmentLabel) throws Exception {
    var req = multipart("/api/auth/register")
        .file(new MockMultipartFile("idCard", "id.jpg", "image/jpeg", PNG))
        .file(new MockMultipartFile("idCardBack", "id-back.jpg", "image/jpeg", PNG_BACK))
        .param("fullName", "Tanvir Mahmud").param("phone", "01712345678").param("email", email)
        .param("universityId", universityId.toString())
        .param("batch", "45")
        .param("address", "Bateshwar, Sylhet-3104")
        .param("studentId", studentId).param("password", "Password1");
    if (departmentId != null) req.param("departmentId", departmentId.toString());
    if (departmentLabel != null) req.param("departmentLabel", departmentLabel);
    return req;
  }

  @Test void registerWithNewCatalogDepartment() throws Exception {
    var f = factory.seedMaster();
    Department swe = muSoftwareEngineering();
    String email = "newcat-" + System.nanoTime() + "@example.com";
    mvc.perform(baseForm(email, "NC-" + System.nanoTime(), f.mu().getId(), swe.getId(), null))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.departmentId").value(swe.getId()))
      .andExpect(jsonPath("$.departmentLabel").value(nullValue()));
    var u = users.findByEmail(email).orElseThrow();
    assertEquals(swe.getId(), u.getDepartmentId(), "the new catalog department row must be stored");
    assertNull(u.getDepartmentLabel(), "no typed label is stored when a department row is chosen");
  }

  @Test void departmentMismatchStillEnforced() throws Exception {
    var f = factory.seedMaster();
    String email = "mismatch-" + System.nanoTime() + "@example.com";
    mvc.perform(baseForm(email, "MM-" + System.nanoTime(), f.mu().getId(), f.duCse().getId(), null))
      .andExpect(status().isBadRequest())
      .andExpect(content().string(org.hamcrest.Matchers.containsString("DEPARTMENT_MISMATCH")));
    assertTrue(users.findByEmail(email).isEmpty(), "a mismatched registration must not create a user");
  }

  @Test void typedLabelStillAcceptedForUnlistedUniversity() throws Exception {
    var f = factory.seedMaster();
    String email = "typed-" + System.nanoTime() + "@example.com";
    mvc.perform(baseForm(email, "TD-" + System.nanoTime(), f.mu().getId(), null, "Robotics and Mechatronics"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.departmentLabel").value("Robotics and Mechatronics"));
    var u = users.findByEmail(email).orElseThrow();
    assertNull(u.getDepartmentId(), "a typed department must not invent a departments row");
    assertEquals("Robotics and Mechatronics", u.getDepartmentLabel(), "the typed label must be stored verbatim");
  }
}
