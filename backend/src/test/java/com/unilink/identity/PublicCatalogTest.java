package com.unilink.identity;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Signup reference data (2026-11-24): the University → Department →
 *  Batch chain must serve real, correctly-related database rows, and an
 *  unknown parent must answer an empty list — never an error that leaves
 *  the signup dropdowns dead. */
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
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class PublicCatalogTest {
  @Autowired MockMvc mvc;
  @Autowired TestDataFactory factory;
  @BeforeEach void seed(){ factory.seedMaster(); }

  @Test void universityListLoadsSeededUniversities() throws Exception {
    mvc.perform(get("/api/public/universities"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].name").value("Metropolitan University"))
      .andExpect(jsonPath("$[0].shortName").value("MU"))
      .andExpect(jsonPath("$[1].name").value("University of Dhaka"))
      .andExpect(jsonPath("$[1].shortName").value("DU"));
  }

  @Test void departmentsBelongToTheirUniversity() throws Exception {
    var f = factory.seedMaster();
    mvc.perform(get("/api/public/departments").param("universityId", f.mu().getId().toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].universityId").value(f.mu().getId()))
      .andExpect(jsonPath("$[0].name").value("CSE — MU"));
    mvc.perform(get("/api/public/departments").param("universityId", f.du().getId().toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].universityId").value(f.du().getId()));
  }

  @Test void batchesBelongToTheirUniversityAndDepartment() throws Exception {
    var f = factory.seedMaster();
    mvc.perform(get("/api/public/batches")
        .param("universityId", f.mu().getId().toString())
        .param("departmentId", f.muCse().getId().toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].label").value("45"))
      .andExpect(jsonPath("$[0].universityId").value(f.mu().getId()))
      .andExpect(jsonPath("$[0].departmentId").value(f.muCse().getId()));
  }

  @Test void unknownParentReturnsEmptyListNotAnError() throws Exception {
    mvc.perform(get("/api/public/departments").param("universityId", "999999"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/public/batches").param("universityId", "999999"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(0));
  }
}
