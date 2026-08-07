package com.wwa.agenthub.platform.integration;

import com.wwa.agenthub.contracts.dto.integration.ArtifactUploadMetadata;
import com.wwa.agenthub.contracts.enums.ArtifactKind;
import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.artifact.ArtifactContentSecurityPolicy;
import com.wwa.agenthub.platform.domain.integration.artifact.KnownSignatureArtifactMalwareScanner;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import com.wwa.agenthub.platform.domain.integration.auth.PresentedCredentialLeakGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactContentSecurityPolicyTest {

    private final PresentedCredentialLeakGuard credentialLeakGuard = new PresentedCredentialLeakGuard();
    private final ArtifactContentSecurityPolicy policy = policy(credentialLeakGuard);

    @Test
    void rejectsCredentialsWithParameterizedTextMediaType() {
        byte[] content = "Authorization: Basic dXNlcjpwYXNz".getBytes(StandardCharsets.UTF_8);

        assertRejected(metadata(ArtifactKind.LOG, "logs/run.txt", "text/plain; charset=utf-8", content),
                "text/plain", "logs/run.txt", content);
    }

    @Test
    void rejectsTheExactPresentedBearerTokenEvenWhenItLooksLikeOrdinaryText() {
        String token = "atlas-test-token-1234567890";
        byte[] content = token.getBytes(StandardCharsets.UTF_8);

        try (var ignored = credentialLeakGuard.bind(token)) {
            assertRejected(metadata(ArtifactKind.REPORT, "report.txt", "text/plain", content),
                    "text/plain", "report.txt", content);
        }
    }

    @Test
    void rejectsNestedEnvironmentAndCredentialPaths() {
        byte[] content = "ordinary".getBytes(StandardCharsets.UTF_8);

        assertRejected(metadata(ArtifactKind.REPORT, "reports/.env", "text/plain", content),
                "text/plain", "reports/.env", content);
        assertRejected(metadata(ArtifactKind.REPORT, "foo/credentials.json", "application/json", content),
                "application/json", "foo/credentials.json", content);
        assertRejected(metadata(ArtifactKind.REPORT, "reports\\.env", "text/plain", content),
                "text/plain", "reports/.env", content);
    }

    @Test
    void rejectsRawSourceButAllowsBoundedPatch() {
        byte[] content = ("diff --git a/App.java b/App.java\n"
                + "--- a/App.java\n+++ b/App.java\n@@ -1 +1 @@\n-old\n+new\n")
                .getBytes(StandardCharsets.UTF_8);

        assertRejected(metadata(ArtifactKind.REPORT, "src/App.java", "text/plain", content),
                "text/plain", "src/App.java", content);
        policy.assertAllowed(
                metadata(ArtifactKind.PATCH, "changes.patch", "text/plain", content),
                "text/plain",
                "changes.patch",
                content);
    }

    @Test
    void rejectsSourceBasenameWhenSourcePathIsAbsent() {
        byte[] content = "record App(int value) {}".getBytes(StandardCharsets.UTF_8);
        ArtifactUploadMetadata metadata = new ArtifactUploadMetadata(
                ArtifactRole.EVIDENCE,
                ArtifactKind.REPORT,
                "App.java",
                "text/plain",
                content.length,
                "0".repeat(64),
                null);

        assertRejected(metadata, "text/plain", null, content);
    }

    @Test
    void rejectsRenamedSourceContent() {
        byte[] javaRecord = "record App(int value) {}".getBytes(StandardCharsets.UTF_8);
        byte[] javascript = "const secret = () => 42;".getBytes(StandardCharsets.UTF_8);
        byte[] sql = "CREATE TABLE hidden_source (id NUMBER);".getBytes(StandardCharsets.UTF_8);

        assertRejected(metadata(ArtifactKind.REPORT, "report.txt", "text/plain", javaRecord),
                "text/plain", "report.txt", javaRecord);
        assertRejected(metadata(ArtifactKind.REPORT, "report.txt", "text/plain", javascript),
                "text/plain", "report.txt", javascript);
        assertRejected(metadata(ArtifactKind.REPORT, "report.txt", "text/plain", sql),
                "text/plain", "report.txt", sql);
    }

    @Test
    void rejectsCompleteSingleLineSourceDisguisedAsPlainEvidence() {
        for (String source : List.of(
                "int main(){return 0;}",
                "print(\"hello\")",
                "return credentials;")) {
            byte[] content = source.getBytes(StandardCharsets.UTF_8);
            assertRejected(metadata(ArtifactKind.REPORT, "report.txt", "text/plain", content),
                    "text/plain", "report.txt", content);
        }
    }

    @Test
    void rejectsSourceWrappedInsideJsonEvidence() {
        for (String json : List.of(
                "{\"source\":\"int main(){return 0;}\"}",
                "{\"int main(){return 0;}\":true}",
                "{\"password\":123456}",
                "{\"password \":123456}",
                "{\"db_password\":\"hunter2\"}",
                "{\"credentials\":\"hunter2\"}",
                "{\"access_token\":true}",
                "{\"private-key\":null}")) {
            byte[] content = json.getBytes(StandardCharsets.UTF_8);
            assertRejected(metadata(ArtifactKind.REPORT, "report.json", "application/json", content),
                    "application/json", "report.json", content);
        }
    }

    @Test
    void allowsOrdinaryStructuredJsonAndMarkdownEvidence() {
        byte[] json = "{\"status\":\"passed\",\"count\":12}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] markdown = ("# Verification report\n\n"
                + "- **Tests:** `mvn test` passed.\n"
                + "- [Build details](https://example.test/build/42)\n")
                .getBytes(StandardCharsets.UTF_8);

        policy.assertAllowed(
                metadata(ArtifactKind.REPORT, "report.json", "application/json", json),
                "application/json",
                "report.json",
                json);
        policy.assertAllowed(
                metadata(ArtifactKind.REPORT, "report.md", "text/markdown", markdown),
                "text/markdown",
                "report.md",
                markdown);
    }

    @Test
    void rejectsStructuredTextAboveItsIndependentMediaLimit() {
        IntegrationClientProperties properties = safeProperties();
        properties.setMaxTextArtifactBytes(12);
        ArtifactContentSecurityPolicy boundedPolicy = policy(properties, credentialLeakGuard);
        byte[] content = "ordinary report evidence".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> boundedPolicy.assertAllowed(
                        metadata(ArtifactKind.REPORT, "report.txt", "text/plain", content),
                        "text/plain",
                        "report.txt",
                        content))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("ARTIFACT_SECURITY_POLICY_VIOLATION"));
    }

    @Test
    void rejectsJsonAboveItsIndependentMediaLimit() {
        IntegrationClientProperties properties = safeProperties();
        properties.setMaxJsonArtifactBytes(16);
        ArtifactContentSecurityPolicy boundedPolicy = policy(properties, credentialLeakGuard);
        byte[] content = "{\"status\":\"ordinary evidence\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> boundedPolicy.assertAllowed(
                        metadata(ArtifactKind.REPORT, "report.json", "application/json", content),
                        "application/json",
                        "report.json",
                        content))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("ARTIFACT_SECURITY_POLICY_VIOLATION"));
    }

    @Test
    void streamsJsonWithExplicitTokenAndNestingBudgets() {
        IntegrationClientProperties tokenProperties = safeProperties();
        tokenProperties.setMaxJsonArtifactTokens(6);
        ArtifactContentSecurityPolicy tokenPolicy = policy(tokenProperties, credentialLeakGuard);
        byte[] manyTokens = "{\"values\":[1,2,3,4]}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> tokenPolicy.assertAllowed(
                        metadata(ArtifactKind.REPORT, "report.json", "application/json", manyTokens),
                        "application/json",
                        "report.json",
                        manyTokens))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("ARTIFACT_SECURITY_POLICY_VIOLATION"));

        IntegrationClientProperties depthProperties = safeProperties();
        depthProperties.setMaxJsonArtifactNestingDepth(2);
        ArtifactContentSecurityPolicy depthPolicy = policy(depthProperties, credentialLeakGuard);
        byte[] deeplyNested = "{\"outer\":{\"inner\":{\"status\":\"passed\"}}}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> depthPolicy.assertAllowed(
                        metadata(ArtifactKind.REPORT, "report.json", "application/json", deeplyNested),
                        "application/json",
                        "report.json",
                        deeplyNested))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("ARTIFACT_SECURITY_POLICY_VIOLATION"));
    }

    @Test
    void rejectsMalformedOrMultipleRootJsonDocuments() {
        for (String json : List.of("{\"status\":", "{} {}")) {
            byte[] content = json.getBytes(StandardCharsets.UTF_8);
            assertRejected(metadata(ArtifactKind.REPORT, "report.json", "application/json", content),
                    "application/json", "report.json", content);
        }
    }

    @Test
    void productionModeFailsClosedWithoutExternalScanner() {
        IntegrationClientProperties properties = new IntegrationClientProperties();
        properties.setRequireExternalArtifactScanner(true);
        ArtifactContentSecurityPolicy productionPolicy = new ArtifactContentSecurityPolicy(
                List.of(new KnownSignatureArtifactMalwareScanner()), properties, new ObjectMapper(),
                new PresentedCredentialLeakGuard());
        byte[] content = "ordinary report".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> productionPolicy.assertAllowed(
                        metadata(ArtifactKind.REPORT, "report.txt", "text/plain", content),
                        "text/plain",
                        "report.txt",
                        content))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("ARTIFACT_SECURITY_POLICY_VIOLATION"));
    }

    @Test
    void requiresPatchBasenameEvenWhenSourcePathIsAbsent() {
        byte[] content = ("--- a/App.java\n+++ b/App.java\n@@ -1 +1 @@\n-old\n+new\n")
                .getBytes(StandardCharsets.UTF_8);
        ArtifactUploadMetadata metadata = new ArtifactUploadMetadata(
                ArtifactRole.EVIDENCE,
                ArtifactKind.PATCH,
                "changes.txt",
                "text/plain",
                content.length,
                "0".repeat(64),
                null);

        assertRejected(metadata, "text/plain", null, content);
    }

    @Test
    void rejectsEicarSignature() {
        byte[] content = ("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-"
                + "TEST-FILE!$H+H*").getBytes(StandardCharsets.US_ASCII);

        assertRejected(metadata(ArtifactKind.BINARY, "scan.bin", "application/octet-stream", content),
                "application/octet-stream", "scan.bin", content);
    }

    @Test
    void rejectsSourceDisguisedAsPatch() {
        byte[] content = "package demo;\npublic class App {}".getBytes(StandardCharsets.UTF_8);

        assertRejected(metadata(ArtifactKind.PATCH, "changes.patch", "text/plain", content),
                "text/plain", "changes.patch", content);
    }

    private void assertRejected(
            ArtifactUploadMetadata metadata,
            String normalizedMediaType,
            String normalizedSourcePath,
            byte[] content
    ) {
        assertThatThrownBy(() -> policy.assertAllowed(
                        metadata, normalizedMediaType, normalizedSourcePath, content))
                .isInstanceOfSatisfying(IntegrationApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo("ARTIFACT_SECURITY_POLICY_VIOLATION"));
    }

    private static ArtifactUploadMetadata metadata(
            ArtifactKind kind,
            String sourcePath,
            String mediaType,
            byte[] content
    ) {
        String normalizedPath = sourcePath.replace('\\', '/');
        String name = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        return new ArtifactUploadMetadata(
                ArtifactRole.EVIDENCE,
                kind,
                name,
                mediaType,
                content.length,
                "0".repeat(64),
                sourcePath);
    }

    private static ArtifactContentSecurityPolicy policy(PresentedCredentialLeakGuard credentialLeakGuard) {
        return policy(safeProperties(), credentialLeakGuard);
    }

    private static IntegrationClientProperties safeProperties() {
        IntegrationClientProperties properties = new IntegrationClientProperties();
        properties.setRequireExternalArtifactScanner(false);
        return properties;
    }

    private static ArtifactContentSecurityPolicy policy(
            IntegrationClientProperties properties,
            PresentedCredentialLeakGuard credentialLeakGuard
    ) {
        return new ArtifactContentSecurityPolicy(
                List.of(new KnownSignatureArtifactMalwareScanner()), properties, new ObjectMapper(),
                credentialLeakGuard);
    }
}
