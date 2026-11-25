package com.unilink.identity;
import com.unilink.identity.domain.DepartmentAlias;
import com.unilink.identity.domain.DepartmentCatalog;
import com.unilink.identity.repo.DepartmentAliasRepository;
import com.unilink.identity.repo.DepartmentCatalogRepository;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
  @Autowired DepartmentCatalogRepository catalogs;
  @Autowired DepartmentAliasRepository aliases;
  @BeforeEach void seed(){ factory.seedMaster(); }

  /** D-198: find-or-create a catalog row + alias for search tests. The
   *  shared H2 database lives across tests in this class, so seeding must
   *  be idempotent. */
  private DepartmentCatalog catalog(String name, String shortName, boolean active){
    return catalogs.findByName(name).orElseGet(() -> {
      DepartmentCatalog c = new DepartmentCatalog(); c.setName(name); c.setShortName(shortName); c.setActive(active);
      return catalogs.save(c);
    });
  }
  private void alias(DepartmentCatalog c, String alias){
    if (aliases.findByAliasIgnoreCase(alias).isEmpty()) {
      DepartmentAlias a = new DepartmentAlias(); a.setCatalogId(c.getId()); a.setAlias(alias); a.setActive(true);
      aliases.save(a);
    }
  }
  private void seedCatalogSearch(){
    DepartmentCatalog eee = catalog("Electrical and Electronic Engineering", "EEE", true);
    alias(eee, "Electrical Engineering");
    DepartmentCatalog swe = catalog("Software Engineering", "SWE", true);
    alias(swe, "Software Engg");
    catalog("Civil Engineering", "Civil", false);
  }

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

  // ---- D-198: department-catalog search ----

  @Test void catalogWithoutQueryReturnsAllActiveRows() throws Exception {
    seedCatalogSearch();
    mvc.perform(get("/api/public/department-catalog"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[*].name", hasItem("Computer Science and Engineering")))
      .andExpect(jsonPath("$[*].name", hasItem("Software Engineering")))
      .andExpect(jsonPath("$[*].name", not(hasItem("Civil Engineering"))));
  }

  @Test void catalogSearchMatchesName() throws Exception {
    seedCatalogSearch();
    mvc.perform(get("/api/public/department-catalog").param("q", "Software"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].name").value("Software Engineering"))
      .andExpect(jsonPath("$[0].shortName").value("SWE"));
  }

  @Test void catalogSearchMatchesShortName() throws Exception {
    seedCatalogSearch();
    mvc.perform(get("/api/public/department-catalog").param("q", "CSE"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[*].name", hasItem("Computer Science and Engineering")));
    // Case-insensitive: "eee" finds EEE.
    mvc.perform(get("/api/public/department-catalog").param("q", "eee"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[*].name", hasItem("Electrical and Electronic Engineering")));
  }

  @Test void catalogSearchMatchesAliasOnly() throws Exception {
    seedCatalogSearch();
    // "engg" appears in no canonical name or short_name here — only the
    // "Software Engg" alias matches, proving the alias join is searched.
    mvc.perform(get("/api/public/department-catalog").param("q", "engg"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].name").value("Software Engineering"))
      .andExpect(jsonPath("$[0].aliases[0]").value("Software Engg"));
  }

  @Test void catalogSearchWithNoMatchReturnsEmptyList() throws Exception {
    seedCatalogSearch();
    mvc.perform(get("/api/public/department-catalog").param("q", "zzz-no-such-department"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(0));
  }

  @Test void departmentsCarryShortNameAndAliases() throws Exception {
    seedCatalogSearch();
    var f = factory.seedMaster();
    mvc.perform(get("/api/public/departments").param("universityId", f.mu().getId().toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].shortName").value("CSE"))
      .andExpect(jsonPath("$[0].aliases").isArray());
  }
}
