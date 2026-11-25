package com.unilink.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * D-188 (2026-11-25): repair failed migrations before migrating.
 *
 * <p>The first auto-deploy of the 9d777cb backend failed on V014 (TiDB
 * rejects a single-statement ADD COLUMN + ADD INDEX on the new column),
 * leaving V014 recorded as failed in flyway_schema_history. Flyway
 * refuses to migrate while a failed migration exists, so without a
 * repair every future deploy would fail at V014 even after the SQL is
 * fixed. repair() only removes rows for FAILED migrations — it never
 * touches a successful one — then migrate() runs the fixed V014 (its
 * CREATE TABLE is IF NOT EXISTS and the failed ALTER applied nothing,
 * so the re-run is clean). This keeps the production database
 * recoverable without manual DB access.
 */
@Configuration
class FlywayConfig {

  @Bean
  FlywayMigrationStrategy flywayMigrationStrategy() {
    return flyway -> {
      flyway.repair();
      flyway.migrate();
    };
  }
}
