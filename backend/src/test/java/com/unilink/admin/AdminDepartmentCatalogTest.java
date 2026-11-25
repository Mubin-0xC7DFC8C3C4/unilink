package com.unilink.admin;

import com.unilink.common.Exceptions;
import com.unilink.identity.domain.DepartmentCatalog;
import com.unilink.identity.dto.CatalogDtos.DepartmentCatalogDto;
import com.unilink.identity.repo.DepartmentAliasRepository;
import com.unilink.identity.repo.DepartmentCatalogRepository;
import com.unilink.security.Principals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D-198: Platform Admin can grow the department catalogue without a
 *  migration; duplicate names and globally-duplicate aliases are 409s
 *  (not 500s from the unique keys), and moderators cannot write it. */
@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-admin-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional
class AdminDepartmentCatalogTest {
  @Autowired AdminService admin;
  @Autowired DepartmentCatalogRepository catalogs;
  @Autowired DepartmentAliasRepository aliases;

  private static final Principals.AdminPrincipal PLATFORM =
      new Principals.AdminPrincipal(1L, "platform@example.com", "hash", "PLATFORM_ADMIN", null, true, true);
  private static final Principals.AdminPrincipal MODERATOR =
      new Principals.AdminPrincipal(2L, "mod@example.com", "hash", "UNIVERSITY_MODERATOR", 1L, true, true);

  @Test void createCatalogEntryWithAliases() {
    DepartmentCatalogDto created = admin.createDepartmentCatalog(PLATFORM, "Data Science", "DS", List.of("Data Sci", "DSC"), "127.0.0.1");
    assertNotNull(created.id());
    assertEquals("Data Science", created.name());
    assertEquals("DS", created.shortName());
    assertEquals(List.of("Data Sci", "DSC"), created.aliases());
    DepartmentCatalog saved = catalogs.findById(created.id()).orElseThrow();
    assertTrue(saved.getActive());
    assertEquals(2, aliases.findByCatalogIdAndActiveTrue(saved.getId()).size());
  }

  @Test void duplicateNameIsConflict() {
    admin.createDepartmentCatalog(PLATFORM, "Data Science", "DS", List.of(), "127.0.0.1");
    assertThrows(Exceptions.Conflict.class,
        () -> admin.createDepartmentCatalog(PLATFORM, "Data Science", "DS2", List.of(), "127.0.0.1"));
  }

  @Test void duplicateAliasIsConflict() {
    admin.createDepartmentCatalog(PLATFORM, "Data Science", "DS", List.of("Data Sci"), "127.0.0.1");
    // The alias is globally unique: a different catalog row may not reuse
    // it, in any casing (the MySQL key is case-insensitive).
    assertThrows(Exceptions.Conflict.class,
        () -> admin.createDepartmentCatalog(PLATFORM, "Statistics", "Stats", List.of("data sci"), "127.0.0.1"));
    assertThrows(Exceptions.Conflict.class,
        () -> admin.addCatalogAlias(PLATFORM,
            admin.createDepartmentCatalog(PLATFORM, "Statistics", "Stats", List.of(), "127.0.0.1").id(),
            "DATA SCI", "127.0.0.1"));
  }

  @Test void addAliasToExistingRow() {
    DepartmentCatalogDto created = admin.createDepartmentCatalog(PLATFORM, "Data Science", "DS", List.of(), "127.0.0.1");
    DepartmentCatalogDto updated = admin.addCatalogAlias(PLATFORM, created.id(), "Data Sci", "127.0.0.1");
    assertEquals(List.of("Data Sci"), updated.aliases());
    assertThrows(Exceptions.NotFound.class,
        () -> admin.addCatalogAlias(PLATFORM, 999999L, "Nope", "127.0.0.1"));
  }

  @Test void moderatorCannotWriteCatalog() {
    assertThrows(Exceptions.Forbidden.class,
        () -> admin.createDepartmentCatalog(MODERATOR, "Data Science", "DS", List.of(), "127.0.0.1"));
  }

  @Test void renameAndDeactivateCatalogRow() {
    DepartmentCatalogDto created = admin.createDepartmentCatalog(PLATFORM, "Data Science", "DS", List.of("Data Sci"), "127.0.0.1");
    DepartmentCatalogDto renamed = admin.updateDepartmentCatalog(PLATFORM, created.id(), "Data Science and AI", "DSAI", null, "127.0.0.1");
    assertEquals("Data Science and AI", renamed.name());
    assertEquals(List.of("Data Sci"), renamed.aliases(), "renaming keeps the aliases");
    admin.createDepartmentCatalog(PLATFORM, "Statistics", "Stats", List.of(), "127.0.0.1");
    assertThrows(Exceptions.Conflict.class,
        () -> admin.updateDepartmentCatalog(PLATFORM, created.id(), "Statistics", null, null, "127.0.0.1"));
    admin.deactivateMasterData(PLATFORM, "department-catalog", created.id(), "127.0.0.1");
    assertFalse(catalogs.findById(created.id()).orElseThrow().getActive());
  }
}
