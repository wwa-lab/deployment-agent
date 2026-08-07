package com.wwa.agenthub.architecture;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Executes V21 against an explicitly provisioned disposable Oracle schema
 * whose real Flyway history and database objects are at V20. Local runs skip
 * unless all three ATLAS_ORACLE_MIGRATION_TEST_* variables are supplied.
 */
class AtlasOracleMigrationIntegrationTest {

    @Test
    void v21MigratesARealOracleV20Schema() {
        String url = System.getenv("ATLAS_ORACLE_MIGRATION_TEST_URL");
        String username = System.getenv("ATLAS_ORACLE_MIGRATION_TEST_USERNAME");
        String password = System.getenv("ATLAS_ORACLE_MIGRATION_TEST_PASSWORD");
        assumeTrue(notBlank(url) && notBlank(username) && notBlank(password),
                "Disposable Oracle migration-test schema is not configured");

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .cleanDisabled(true)
                .load();
        var before = flyway.info().current();
        assertThat(before)
                .as("The disposable Oracle schema must be pre-provisioned at Flyway V20")
                .isNotNull();
        assertThat(before.getVersion().getVersion())
                .as("The disposable Oracle schema must start exactly at V20")
                .isEqualTo("20");

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("21");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
