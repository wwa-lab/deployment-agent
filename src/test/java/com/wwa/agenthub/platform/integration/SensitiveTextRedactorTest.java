package com.wwa.agenthub.platform.integration;

import com.wwa.agenthub.platform.domain.integration.SensitiveTextRedactor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveTextRedactorTest {

    @Test
    void redactsCredentialAssignmentsBearerValuesAndKnownTokenFormats() {
        String input = "token=super-secret-value Authorization: Bearer abcdefghijklmnop "
                + "github=ghp_abcdefghijklmnopqrstuvwxyz123456 sk-abcdefghijklmnopqrstuvwxyz";

        String redacted = SensitiveTextRedactor.redact(input);

        assertThat(redacted)
                .contains("token=[REDACTED]")
                .contains("Authorization: [REDACTED]")
                .doesNotContain("super-secret-value")
                .doesNotContain("abcdefghijklmnop")
                .doesNotContain("ghp_abcdefghijklmnopqrstuvwxyz123456")
                .doesNotContain("sk-abcdefghijklmnopqrstuvwxyz");
    }

    @Test
    void preservesOrdinaryOperationalMessages() {
        assertThat(SensitiveTextRedactor.redact("Access token expired while publishing artifact 4"))
                .isEqualTo("Access token expired while publishing artifact 4");
        assertThat(SensitiveTextRedactor.redact(null)).isNull();
    }

    @Test
    void redactsEnvironmentCookiesCredentialUrisAndQuerySecrets() {
        String input = "API_KEY=key-123 DATABASE_URL=postgres://deploy:db-pass@example/db "
                + "COOKIE=session-value callback=https://example.test?a=1&access_token=query-secret";

        String redacted = SensitiveTextRedactor.redact(input);

        assertThat(redacted)
                .contains("API_KEY=[REDACTED]")
                .contains("DATABASE_URL=[REDACTED]")
                .contains("COOKIE=[REDACTED]")
                .doesNotContain("key-123")
                .doesNotContain("db-pass")
                .doesNotContain("session-value")
                .doesNotContain("query-secret");
    }

    @Test
    void redactsEntireAuthorizationValuesAcrossSchemes() {
        String input = "Authorization: Basic dXNlcjpwYXNz\n"
                + "Proxy-Authorization=Token opaque-credential\n"
                + "authorization: ApiKey abc.def.ghi";

        String redacted = SensitiveTextRedactor.redact(input);

        assertThat(redacted)
                .contains("Authorization: [REDACTED]")
                .contains("Proxy-Authorization=[REDACTED]")
                .contains("authorization: [REDACTED]")
                .doesNotContain("dXNlcjpwYXNz")
                .doesNotContain("opaque-credential")
                .doesNotContain("abc.def.ghi");
    }

    @Test
    void boundsAfterRedactionExpansion() {
        String input = "token=x ".repeat(500);

        assertThat(SensitiveTextRedactor.redact(input, 2000)).hasSize(2000);
    }

    @Test
    void operationalProjectionSuppressesSourceLikeTextButKeepsSafeFailureDetail() {
        assertThat(SensitiveTextRedactor.redactOperational(
                "record Account(String id) {}", 2000))
                .isEqualTo("[REDACTED UNSAFE OPERATIONAL CONTENT]");
        assertThat(SensitiveTextRedactor.redactOperational(
                "Deployment timed out while waiting for approval", 2000))
                .isEqualTo("Deployment timed out while waiting for approval");
    }

    @Test
    void operationalProjectionSuppressesMultilineAndCommonStructuredSourceFormats() {
        assertThat(SensitiveTextRedactor.redactOperational(
                "apiVersion: v1", 2000))
                .isEqualTo("[REDACTED UNSAFE OPERATIONAL CONTENT]");
        assertThat(SensitiveTextRedactor.redactOperational(
                "<project><name>atlas</name></project>", 2000))
                .isEqualTo("[REDACTED UNSAFE OPERATIONAL CONTENT]");
        assertThat(SensitiveTextRedactor.redactOperational(
                "resource \"aws_instance\" \"atlas\" {", 2000))
                .isEqualTo("[REDACTED UNSAFE OPERATIONAL CONTENT]");
        assertThat(SensitiveTextRedactor.redactOperational(
                "IDENTIFICATION DIVISION.", 2000))
                .isEqualTo("[REDACTED UNSAFE OPERATIONAL CONTENT]");
        assertThat(SensitiveTextRedactor.redactOperational(
                "**FREE", 2000))
                .isEqualTo("[REDACTED UNSAFE OPERATIONAL CONTENT]");
        assertThat(SensitiveTextRedactor.redactOperational(
                "Safe first line\nraw log continuation", 2000))
                .isEqualTo("[REDACTED UNSAFE OPERATIONAL CONTENT]");
    }

    @Test
    void evidenceTextUsesANarrowPositivePolicy() {
        assertThat(SensitiveTextRedactor.isSafeEvidenceText(
                "12 tests passed in 42 seconds.")).isTrue();
        assertThat(SensitiveTextRedactor.isSafeEvidenceText(
                "int main(){return 0;}")).isFalse();
        assertThat(SensitiveTextRedactor.isSafeEvidenceText(
                "print(\"hello\")")).isFalse();
        assertThat(SensitiveTextRedactor.isSafeEvidenceText(
                "return credentials;")).isFalse();
        assertThat(SensitiveTextRedactor.isSafeEvidenceText(
                "2026-08-07 10:00:00 INFO Started\n"
                        + "2026-08-07 10:00:01 INFO Done")).isFalse();
        assertThat(SensitiveTextRedactor.isSafeEvidenceText(
                "2026-08-07 10:00:00 atlas INFO Started\n"
                        + "2026-08-07 10:00:01 atlas INFO Done")).isFalse();
        assertThat(SensitiveTextRedactor.isSafeEvidenceText(
                "Aug 7 10:00:00 atlas sshd Connection accepted\n"
                        + "Aug 7 10:00:01 atlas sshd Session opened")).isFalse();
    }

    @Test
    void markdownEvidenceAllowsFormattingButRejectsCodePayloads() {
        assertThat(SensitiveTextRedactor.isSafeMarkdownEvidenceText(
                "# Verification\n\n- **Tests:** `mvn test` passed.\n"
                        + "- [Details](https://example.test/build/42)"))
                .isTrue();
        assertThat(SensitiveTextRedactor.isSafeMarkdownEvidenceText(
                "# Source\n\n`int main(){return 0;}`"))
                .isFalse();
    }
}
