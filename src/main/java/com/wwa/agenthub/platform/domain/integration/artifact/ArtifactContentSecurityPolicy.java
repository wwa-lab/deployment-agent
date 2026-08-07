package com.wwa.agenthub.platform.domain.integration.artifact;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.contracts.dto.integration.ArtifactUploadMetadata;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.SensitiveTextRedactor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import com.wwa.agenthub.platform.domain.integration.auth.PresentedCredentialLeakGuard;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Built-in fail-closed policy for prohibited Atlas Artifact payloads. */
@Component
public class ArtifactContentSecurityPolicy {

    private static final Set<String> TEXT_MEDIA_TYPES = Set.of(
            "text/plain", "text/markdown", "application/json");
    private static final Pattern ENV_ASSIGNMENT = Pattern.compile(
            "(?m)^\\s*(?:export\\s+)?[A-Za-z_][A-Za-z0-9_]{1,127}\\s*=\\s*\\S+\\s*$");
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)\"(?:authorization|access[_-]?token|api[_-]?(?:token|key)|refresh[_-]?token|"
                    + "id[_-]?token|password|passwd|secret|client[_-]?secret|cookie|session(?:id)?)\""
                    + "\\s*:\\s*\"[^\"]+\"");
    private static final Set<String> JSON_SECRET_FIELD_TOKENS = Set.of(
            "authorization", "auth", "token", "password", "passwd", "secret", "secrets",
            "credential", "credentials", "cookie", "session");
    private static final Pattern PROMPT_EXPORT = Pattern.compile(
            "(?im)^\\s*(?:system|developer|assistant)\\s+(?:prompt|message)\\s*:");
    private static final Pattern FORBIDDEN_PATH = Pattern.compile(
            "(?i)(?:^|/)(?:\\.env(?:\\.[^/]+)?|credentials?(?:\\.[^/]+)?|"
                    + "id_(?:rsa|dsa|ecdsa|ed25519)|.*(?:system|developer)[_-]?prompt.*)$");
    private static final Pattern SOURCE_PATH = Pattern.compile(
            "(?i).+\\.(?:c|cc|cpp|cxx|h|hpp|java|kt|kts|scala|groovy|py|rb|php|"
                    + "go|rs|cs|swift|js|jsx|ts|tsx|vue|svelte|sh|bash|zsh|sql)$");
    private static final Pattern PATCH_PATH = Pattern.compile("(?i).+\\.(?:diff|patch)$");
    private static final Pattern UNIFIED_DIFF = Pattern.compile(
            "(?s)^(?:diff --git [^\\r\\n]+\\R)?--- [^\\r\\n]+\\R\\+\\+\\+ [^\\r\\n]+\\R.*"
                    + "^@@ [^\\r\\n]+ @@",
            Pattern.MULTILINE);
    private static final Pattern SOURCE_CONTENT = Pattern.compile(
            "(?m)^\\s*(?:package\\s+[A-Za-z_]|import\\s+[A-Za-z_].*;|#include\\s*[<\"]|"
                    + "(?:(?:public|private|protected)\\s+)?(?:final\\s+)?"
                    + "(?:class|interface|enum|record)\\s+[A-Za-z_]|"
                    + "(?:def|func|function)\\s+[A-Za-z_][A-Za-z0-9_]*|"
                    + "(?:const|let|var)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*=|"
                    + "(?:CREATE|ALTER|DROP)\\s+(?:TABLE|VIEW|INDEX|PROCEDURE|FUNCTION)\\s+|"
                    + "SELECT\\s+.+\\s+FROM\\s+)", Pattern.CASE_INSENSITIVE);

    private final List<ArtifactMalwareScanner> malwareScanners;
    private final boolean requireExternalScanner;
    private final ObjectMapper objectMapper;
    private final PresentedCredentialLeakGuard credentialLeakGuard;
    private final long maxTextArtifactBytes;
    private final long maxJsonArtifactBytes;
    private final int maxJsonArtifactTokens;
    private final int maxJsonArtifactNestingDepth;

    public ArtifactContentSecurityPolicy(
            List<ArtifactMalwareScanner> malwareScanners,
            IntegrationClientProperties properties,
            ObjectMapper objectMapper,
            PresentedCredentialLeakGuard credentialLeakGuard
    ) {
        this.malwareScanners = List.copyOf(malwareScanners);
        this.requireExternalScanner = properties.isRequireExternalArtifactScanner();
        this.objectMapper = objectMapper;
        this.credentialLeakGuard = credentialLeakGuard;
        this.maxTextArtifactBytes = boundedSize(
                properties.getMaxTextArtifactBytes(), properties.getMaxArtifactBytes());
        this.maxJsonArtifactBytes = boundedSize(
                properties.getMaxJsonArtifactBytes(), properties.getMaxArtifactBytes());
        this.maxJsonArtifactTokens = Math.max(1, properties.getMaxJsonArtifactTokens());
        this.maxJsonArtifactNestingDepth = Math.max(1, properties.getMaxJsonArtifactNestingDepth());
    }

    public void assertAllowed(
            ArtifactUploadMetadata metadata,
            String normalizedMediaType,
            String normalizedSourcePath,
            byte[] content
    ) {
        if (credentialLeakGuard.contains(metadata.name())
                || credentialLeakGuard.contains(metadata.kind())
                || credentialLeakGuard.contains(normalizedSourcePath)
                || credentialLeakGuard.contains(content)) {
            reject("Artifact metadata or content contains the presented credential.");
        }
        assertStructuredTextSize(normalizedMediaType, content.length);
        if (malwareScanners.isEmpty()) {
            reject("Artifact malware scanning is unavailable.");
        }
        if (requireExternalScanner
                && malwareScanners.stream().noneMatch(ArtifactMalwareScanner::productionReady)) {
            reject("A production Artifact malware and DLP scanner is required.");
        }
        for (ArtifactMalwareScanner scanner : malwareScanners) {
            scanner.assertClean(metadata, content);
        }
        String sourcePath = normalizedSourcePath;
        String artifactName = metadata.name().trim();
        if (sourcePath != null && FORBIDDEN_PATH.matcher(sourcePath.trim()).find()) {
            reject("Environment, credential, and prompt files cannot be stored as Artifacts.");
        }
        if (SOURCE_PATH.matcher(artifactName).matches()
                || sourcePath != null && SOURCE_PATH.matcher(sourcePath).matches()) {
            reject("Raw source files cannot be stored as Artifacts; upload a bounded patch instead.");
        }
        if (!TEXT_MEDIA_TYPES.contains(normalizedMediaType)) {
            return;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        boolean patch = "PATCH".equalsIgnoreCase(metadata.kind());
        if (patch && (!PATCH_PATH.matcher(artifactName).matches()
                || sourcePath != null && !PATCH_PATH.matcher(sourcePath).matches()
                || !UNIFIED_DIFF.matcher(text).find())) {
            reject("PATCH Artifacts must be a bounded unified diff with a .patch or .diff label.");
        }
        if (!patch && SOURCE_CONTENT.matcher(text).find()) {
            reject("Raw source content cannot be stored as an Artifact.");
        }
        if (!patch
                && "text/plain".equals(normalizedMediaType)
                && !SensitiveTextRedactor.isSafeEvidenceText(text)) {
            reject("Text Artifacts must contain bounded plain evidence, not source, configuration, or raw logs.");
        }
        if (!patch
                && "text/markdown".equals(normalizedMediaType)
                && !SensitiveTextRedactor.isSafeMarkdownEvidenceText(text)) {
            reject("Markdown Artifacts must contain bounded prose evidence without source or fenced code.");
        }
        if (!patch && "application/json".equals(normalizedMediaType)) {
            assertSafeJsonEvidence(content);
        }
        if (!SensitiveTextRedactor.redact(text).equals(text)
                || JSON_SECRET.matcher(text).find()
                || ENV_ASSIGNMENT.matcher(text).find()
                || PROMPT_EXPORT.matcher(text).find()) {
            reject("Artifact content contains prohibited credentials, environment data, or prompts.");
        }
    }

    private void assertSafeJsonEvidence(byte[] content) {
        try (JsonParser parser = objectMapper.getFactory().createParser(content)) {
            int tokenCount = 0;
            int nestingDepth = 0;
            int rootValues = 0;
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                tokenCount++;
                if (tokenCount > maxJsonArtifactTokens) {
                    reject("JSON Artifact content exceeds the configured token budget.");
                }
                if (nestingDepth == 0 && (token.isScalarValue()
                        || token == JsonToken.START_OBJECT
                        || token == JsonToken.START_ARRAY)) {
                    rootValues++;
                    if (rootValues > 1) {
                        reject("JSON Artifact content must contain exactly one root value.");
                    }
                }
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    nestingDepth++;
                    if (nestingDepth > maxJsonArtifactNestingDepth) {
                        reject("JSON Artifact content exceeds the configured nesting depth.");
                    }
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    nestingDepth--;
                } else if (token == JsonToken.FIELD_NAME) {
                    assertSafeJsonField(parser.currentName());
                } else if (token == JsonToken.VALUE_STRING) {
                    assertSafeJsonText(parser.getText());
                }
            }
            if (rootValues != 1 || nestingDepth != 0) {
                reject("JSON Artifact content is invalid.");
            }
        } catch (IntegrationApiException exception) {
            throw exception;
        } catch (Exception exception) {
            reject("JSON Artifact content is invalid.");
        }
    }

    private static void assertSafeJsonField(String fieldName) {
        if (isSensitiveJsonField(fieldName)
                || !SensitiveTextRedactor.isSafeEvidenceText(fieldName)) {
            reject("JSON Artifact field names cannot contain credentials or source content.");
        }
    }

    private static void assertSafeJsonText(String value) {
        if (!SensitiveTextRedactor.isSafeEvidenceText(value)) {
            reject("JSON Artifact text must be bounded evidence, not source, configuration, or raw logs.");
        }
    }

    private void assertStructuredTextSize(String mediaType, int contentLength) {
        if ("application/json".equals(mediaType) && contentLength > maxJsonArtifactBytes) {
            reject("JSON Artifact content exceeds the configured structured-data limit.");
        }
        if (("text/plain".equals(mediaType) || "text/markdown".equals(mediaType))
                && contentLength > maxTextArtifactBytes) {
            reject("Text Artifact content exceeds the configured evidence limit.");
        }
    }

    private static long boundedSize(long configured, long absoluteMaximum) {
        return Math.max(1L, Math.min(configured, absoluteMaximum));
    }

    private static boolean isSensitiveJsonField(String fieldName) {
        String canonical = fieldName.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (canonical.isEmpty()) {
            return true;
        }
        for (String token : canonical.split("_")) {
            if (JSON_SECRET_FIELD_TOKENS.contains(token)) {
                return true;
            }
        }
        return canonical.contains("api_key")
                || canonical.contains("private_key")
                || canonical.contains("database_url")
                || canonical.contains("connection_string")
                || canonical.contains("connection_url");
    }

    private static void reject(String message) {
        throw IntegrationApiException.unprocessable("ARTIFACT_SECURITY_POLICY_VIOLATION", message);
    }
}
