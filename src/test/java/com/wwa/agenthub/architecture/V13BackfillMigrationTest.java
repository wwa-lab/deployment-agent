package com.wwa.agenthub.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BA-T26.1 — verifies that the V13 backfill migration file exists and contains the
 * expected UPDATE statement.
 *
 * <p>Rationale: V13 is a one-time data migration that resolves P-01 (build-agent-tasks.md
 * §10). Without it, legacy {@code DA_REQUEST} rows whose {@code agent} column is NULL
 * would be invisible to the Deployment Agent list after BA-T12 switched to
 * {@code listByAgent}. The invariant "no DA_REQUEST row has agent IS NULL" must hold
 * in every environment that has upgraded past v3.
 *
 * <p>The H2 test profile starts with an empty schema, so a runtime SQL check is trivially
 * true; the meaningful protection is that the V13 migration file remains in the repo
 * with the correct statement. This test therefore reads the SQL file directly and pins
 * its content.
 */
@DisplayName("BA-T26.1 — V13 backfill migration invariant")
class V13BackfillMigrationTest {

    private static final Path V13_PATH = Path.of(
            "src/main/resources/db/migration/V13__backfill_null_agent_to_deployment_agent.sql");

    @Test
    @DisplayName("V13 migration file exists")
    void v13Exists() {
        assertThat(Files.exists(V13_PATH))
                .as("V13 backfill migration file must remain in src/main/resources/db/migration/")
                .isTrue();
    }

    @Test
    @DisplayName("V13 migration updates null-agent rows to deployment-agent")
    void v13ContainsExpectedUpdateStatement() throws Exception {
        String content = Files.readString(V13_PATH);
        assertThat(content)
                .contains("UPDATE DA_REQUEST")
                .contains("SET agent = 'deployment-agent'")
                .contains("WHERE agent IS NULL");
    }
}
