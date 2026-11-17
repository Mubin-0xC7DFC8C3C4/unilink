package com.unilink.arch;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;
/** ArchUnit layering (TEST_STRATEGY §2): controllers never touch repositories or entities directly. */
class ArchitectureTest {
  private final var classes = new ClassFileImporter().importPackages("com.unilink");
  @Test void controllersDoNotAccessRepositories(){
    ArchRuleDefinition.noClasses().that().resideInAPackage("..api..")
      .should().dependOnClassesThat().resideInAPackage("..repo..")
      .check(classes);
  }
  @Test void controllersDoNotDependOnEntities(){
    ArchRuleDefinition.noClasses().that().resideInAPackage("..api..")
      .should().dependOnClassesThat().resideInAPackage("..domain..")
      .check(classes);
  }
  @Test void servicesDoNotDependOnControllers(){
    ArchRuleDefinition.noClasses().that().resideInAPackage("..service..")
      .should().dependOnClassesThat().resideInAPackage("..api..")
      .check(classes);
  }
}
