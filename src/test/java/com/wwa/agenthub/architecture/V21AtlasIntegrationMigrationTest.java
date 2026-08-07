package com.wwa.agenthub.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V21AtlasIntegrationMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V21__add_atlas_integration_platform.sql");
    private static final Path GREENFIELD = Path.of("docs/sql/ORACLE_CURRENT_SCHEMA.sql");

    @Test
    void migrationDefinesPlatformTablesConstraintsAndIndexes() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION).toUpperCase();

        assertThat(sql)
                .contains("ACTIVE_EXECUTION_ID")
                .contains("CAPABILITY_ID")
                .contains("CLIENT_TYPE")
                .contains("REPOSITORY_URL")
                .contains("DA_EXECUTION_EVENT")
                .contains("DA_INTEGRATION_ARTIFACT")
                .contains("DA_TASK_INPUT_ARTIFACT")
                .contains("DA_INTEGRATION_REVIEW")
                .contains("DA_INTEGRATION_IDEMPOTENCY")
                .contains("UNIQUE")
                .contains("FOREIGN KEY");

        String compact = sql.replaceAll("\\s+", " ");
        assertThat(compact)
                .contains("CK_IA_SIZE CHECK (SIZE_BYTES >= 0)")
                .contains("FK_TASK_ACTIVE_EXECUTION FOREIGN KEY (ACTIVE_EXECUTION_ID, ID)")
                .contains("UK_TEH_ONE_RUNNING_INTEGRATION")
                .contains("FK_EE_EXEC_TASK FOREIGN KEY (EXECUTION_ID, TASK_ID)")
                .contains("FK_IA_EXEC_TASK FOREIGN KEY (EXECUTION_ID, TASK_ID)")
                .contains("FK_IR_EXEC_TASK FOREIGN KEY (EXECUTION_ID, TASK_ID)")
                .contains("CK_IA_SHA256 CHECK (REGEXP_LIKE(SHA256, '^[0-9A-F]{64}$'))")
                .contains("CONTENT_EXPIRES_AT")
                .contains("CK_IA_LEGAL_HOLD")
                .contains("IDX_IA_RETENTION")
                .contains("CASE WHEN SEQUENCE_NUMBER IS NOT NULL THEN EXECUTION_ID END")
                .contains("UK_II_COMMAND UNIQUE ( PRINCIPAL_ID, HTTP_METHOD, CANONICAL_PATH, IDEMPOTENCY_KEY_HASH )");

        assertThat(sql)
                .doesNotContain("RAW_TOKEN")
                .doesNotContain("ACCESS_TOKEN")
                .doesNotContain("SIGNED_URL");
    }

    @Test
    void greenfieldSchemaMatchesV21EndState() throws Exception {
        String sql = Files.readString(GREENFIELD).toUpperCase();

        assertThat(sql)
                .contains("V2 THROUGH V21")
                .contains("DA_EXECUTION_EVENT")
                .contains("DA_INTEGRATION_ARTIFACT")
                .contains("DA_TASK_INPUT_ARTIFACT")
                .contains("DA_INTEGRATION_REVIEW")
                .contains("DA_INTEGRATION_IDEMPOTENCY");
    }
}
