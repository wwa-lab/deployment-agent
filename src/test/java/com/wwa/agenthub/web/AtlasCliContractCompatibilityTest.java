package com.wwa.agenthub.web;

import com.jayway.jsonpath.JsonPath;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.integration.clients[0].token-sha256=c4d19bf88bd6dc3bccd99b9e60887d1f6036d90d7c0d33676c2f067b6c8e5a43",
        "app.integration.clients[0].application-id=atlas-copilot-test",
        "app.integration.clients[0].client-type=COPILOT",
        "app.integration.clients[0].client-version=1.2.3",
        "app.integration.clients[0].user-id=alice",
        "app.integration.clients[0].display-name=Alice",
        "app.integration.clients[0].roles[0]=DEVELOPER",
        "app.integration.clients[0].scopes[0]=*|*",
        "app.integration.clients[0].allowed-agents[0]=deployment-agent"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AtlasCliContractCompatibilityTest {

    private static final String TOKEN = "atlas-test-token-1234567890";
    private static final String COMMIT = "abcdef1234567890abcdef1234567890abcdef12";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;

    private Task task;

    @BeforeEach
    void setUp() {
        ReleaseFlow flow = helper.seedReleaseFlow();
        Request request = helper.seedRequest(flow);
        task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.SKILL, "alice");
    }

    @Test
    void taskResourcesUseThePublishedAtlasCliShapeAndApprovedInputPath() throws Exception {
        mockMvc.perform(get("/api/v1/integration/tasks/{id}", task.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(task.getId()))
                .andExpect(jsonPath("$.data.workItemId").isNotEmpty())
                .andExpect(jsonPath("$.data.agentModuleId").value("deployment-agent"))
                .andExpect(jsonPath("$.data.status").value("READY_FOR_EXECUTION"))
                .andExpect(jsonPath("$.data.assignee.userId").value("alice"))
                .andExpect(jsonPath("$.data.capability.capabilityId").value("skill.atlas.delivery"))
                .andExpect(jsonPath("$.data.projectContext.project.projectId").value("PROJ-001"))
                .andExpect(jsonPath("$.data.projectContext.repository.repositoryId").value("repo-atlas-001"))
                .andExpect(jsonPath("$.data.projectContext.repository.url").value(
                        "https://github.example.invalid/wwa/atlas.git"))
                .andExpect(jsonPath("$.data.projectContext.branch").value("main"))
                .andExpect(jsonPath("$.data.projectContext.commit").value(COMMIT))
                .andExpect(jsonPath("$.data.approvedInputArtifactIds").isArray())
                .andExpect(jsonPath("$.data.executionCount").value(0))
                .andExpect(jsonPath("$.data.inputParameters").doesNotExist());

        mockMvc.perform(get("/api/v1/integration/tasks")
                        .param("agentModuleId", "deployment-agent")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.hasMore").value(false))
                .andExpect(jsonPath("$.page").doesNotExist());

        mockMvc.perform(get("/api/v1/integration/tasks/{id}/approved-input-artifacts", task.getId())
                        .param("limit", "100")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.hasMore").value(false));
    }

    @Test
    void createProgressAndArtifactEndpointsMatchThePublishedAtlasCliContract() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "contract-start-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("skill.atlas.delivery")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptNumber").value(1))
                .andExpect(jsonPath("$.data.attempt").doesNotExist())
                .andExpect(jsonPath("$.data.user.userId").value("alice"))
                .andExpect(jsonPath("$.data.client.clientType").value("COPILOT"))
                .andExpect(jsonPath("$.data.client.clientVersion").value("1.2.3"))
                .andExpect(jsonPath("$.data.projectContext.repository.url").value(
                        "https://github.example.invalid/wwa/atlas.git"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data.failureReason").doesNotExist())
                .andReturn();
        String executionId = JsonPath.read(start.getResponse().getContentAsString(), "$.data.executionId");

        mockMvc.perform(post("/api/v1/integration/executions/{id}/progress-events", executionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "contract-progress-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sequenceNumber\":1,\"percent\":25,\"message\":\"Tests running\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.progressEventId").isNotEmpty())
                .andExpect(jsonPath("$.data.executionId").value(executionId))
                .andExpect(jsonPath("$.data.sequenceNumber").value(1))
                .andExpect(jsonPath("$.data.percent").value(25))
                .andExpect(jsonPath("$.data.recordedAt").isNotEmpty());

        byte[] content = "tests passed".getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        String metadataJson = "{\"role\":\"EVIDENCE\",\"kind\":\"REPORT\","
                + "\"name\":\"test-summary.txt\",\"mediaType\":\"text/plain\","
                + "\"sizeBytes\":" + content.length + ","
                + "\"digest\":{\"algorithm\":\"SHA-256\",\"value\":\"" + digest + "\"},"
                + "\"sourcePath\":\"reports/test-summary.txt\"}";
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                metadataJson.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile artifactContent = new MockMultipartFile(
                "content", "test-summary.txt", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);

        MvcResult upload = mockMvc.perform(multipart(
                                "/api/v1/integration/executions/{id}/artifacts", executionId)
                        .file(metadata)
                        .file(artifactContent)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "contract-artifact-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("REPORT"))
                .andExpect(jsonPath("$.data.digest.algorithm").value("SHA-256"))
                .andExpect(jsonPath("$.data.digest.value").value(digest))
                .andExpect(jsonPath("$.data.content.mode").value("UPLOAD"))
                .andExpect(jsonPath("$.data.sourcePath").value("reports/test-summary.txt"))
                .andReturn();
        String artifactId = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.artifactId");

        mockMvc.perform(get("/api/v1/integration/executions/{executionId}/artifacts/{artifactId}/content",
                                executionId, artifactId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Digest",
                        "sha-256=:" + Base64.getEncoder().encodeToString(
                                HexFormat.of().parseHex(digest)) + ":"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        MvcResult reference = mockMvc.perform(post(
                                "/api/v1/integration/executions/{id}/artifacts", executionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "contract-reference-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metadata\":" + metadataJson
                                + ",\"referenceId\":\"" + artifactId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content.mode").value("REFERENCE"))
                .andExpect(jsonPath("$.data.content.referenceId").value(artifactId))
                .andExpect(jsonPath("$.data.digest.value").value(digest))
                .andReturn();
        String referenceId = JsonPath.read(
                reference.getResponse().getContentAsString(), "$.data.artifactId");

        mockMvc.perform(get("/api/v1/integration/executions/{executionId}/artifacts/{artifactId}/content",
                                executionId, referenceId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Digest"));

        mockMvc.perform(get("/api/v1/integration/executions/{id}/artifacts", executionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content.mode").value("UPLOAD"))
                .andExpect(jsonPath("$.data[0].contentBlob").doesNotExist());

        mockMvc.perform(post("/api/v1/integration/executions/{id}/submit", executionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "contract-submit-omit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"done\",\"artifactIds\":[\"" + artifactId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/integration/executions/{id}/submit", executionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "contract-submit-all-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"done\",\"artifactIds\":[\""
                                + artifactId + "\",\"" + referenceId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.artifactCount").value(2));
    }

    @Test
    void createRejectsCapabilityAssertionsThatDoNotMatchTheAtlasTask() throws Exception {
        mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "contract-mismatch-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("skill.someone-else")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPABILITY_MISMATCH"));
    }

    @Test
    void artifactPolicyUsesStableDigestAndMediaErrorsAndAllowsZeroByteEvidence() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "policy-start-000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("skill.atlas.delivery")))
                .andExpect(status().isCreated())
                .andReturn();
        String executionId = JsonPath.read(start.getResponse().getContentAsString(), "$.data.executionId");

        byte[] evidence = "safe evidence".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile badDigestMetadata = metadataPart(
                "EVIDENCE", "REPORT", "evidence.txt", "text/plain", evidence.length, "0".repeat(64));
        mockMvc.perform(multipart("/api/v1/integration/executions/{id}/artifacts", executionId)
                        .file(badDigestMetadata)
                        .file(new MockMultipartFile(
                                "content", "evidence.txt", MediaType.APPLICATION_OCTET_STREAM_VALUE, evidence))
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "policy-digest-00001"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ARTIFACT_DIGEST_MISMATCH"));

        byte[] zip = new byte[]{0x50, 0x4b, 0x03, 0x04, 0x00};
        String zipDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));
        mockMvc.perform(multipart("/api/v1/integration/executions/{id}/artifacts", executionId)
                        .file(metadataPart(
                                "OUTPUT", "BINARY", "bundle.zip", "application/octet-stream",
                                zip.length, zipDigest))
                        .file(new MockMultipartFile(
                                "content", "bundle.zip", MediaType.APPLICATION_OCTET_STREAM_VALUE, zip))
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "policy-archive-0001"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));

        byte[] empty = new byte[0];
        String emptyDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(empty));
        mockMvc.perform(multipart("/api/v1/integration/executions/{id}/artifacts", executionId)
                        .file(metadataPart(
                                "EVIDENCE", "LOG", "empty.txt", "text/plain", 0, emptyDigest))
                        .file(new MockMultipartFile(
                                "content", "empty.txt", MediaType.APPLICATION_OCTET_STREAM_VALUE, empty))
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "policy-empty-000001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sizeBytes").value(0));
    }

    @Test
    void structuredFailureAndCancellationUseCommandsInsteadOfArbitraryTaskStatus() throws Exception {
        MvcResult failedStart = mockMvc.perform(post(
                                "/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "failure-start-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("skill.atlas.delivery")))
                .andExpect(status().isCreated())
                .andReturn();
        String failedExecutionId = JsonPath.read(
                failedStart.getResponse().getContentAsString(), "$.data.executionId");

        mockMvc.perform(post("/api/v1/integration/executions/{id}/fail", failedExecutionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "failure-command-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReason\":{\"code\":\"TEST_FAILURE\","
                                + "\"message\":\"Tests failed safely\",\"retryable\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason.code").value("TEST_FAILURE"))
                .andExpect(jsonPath("$.data.failureReason.retryable").value(true));

        Task cancelledTask = helper.seedIntegrationTask(
                task.getRequest(), TaskStatus.Ready_For_Execution, CapabilityType.SKILL, "alice");
        MvcResult cancelledStart = mockMvc.perform(post(
                                "/api/v1/integration/tasks/{id}/executions", cancelledTask.getId())
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "cancel-start-000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("skill.atlas.delivery")))
                .andExpect(status().isCreated())
                .andReturn();
        String cancelledExecutionId = JsonPath.read(
                cancelledStart.getResponse().getContentAsString(), "$.data.executionId");

        mockMvc.perform(post("/api/v1/integration/executions/{id}/cancel", cancelledExecutionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "cancel-command-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"operator requested cancellation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value(
                        "operator requested cancellation"));

        mockMvc.perform(get("/api/v1/integration/tasks/{id}", cancelledTask.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_EXECUTION"))
                .andExpect(jsonPath("$.data.activeExecutionId").doesNotExist());
    }

    private String bearer() {
        return "Bearer " + TOKEN;
    }

    private String createExecutionBody(String capabilityId) {
        return "{"
                + "\"clientVersion\":\"1.2.3\","
                + "\"capability\":{"
                + "\"capabilityId\":\"" + capabilityId + "\","
                + "\"capabilityType\":\"SKILL\","
                + "\"capabilityVersion\":\"1.0.0\"},"
                + "\"projectContext\":{"
                + "\"projectId\":\"PROJ-001\","
                + "\"repositoryId\":\"repo-atlas-001\","
                + "\"branch\":\"main\","
                + "\"commit\":\"" + COMMIT + "\"}}";
    }

    private static MockMultipartFile metadataPart(
            String role,
            String kind,
            String name,
            String mediaType,
            long sizeBytes,
            String digest
    ) {
        String json = "{"
                + "\"role\":\"" + role + "\","
                + "\"kind\":\"" + kind + "\","
                + "\"name\":\"" + name + "\","
                + "\"mediaType\":\"" + mediaType + "\","
                + "\"sizeBytes\":" + sizeBytes + ","
                + "\"digest\":{\"algorithm\":\"SHA-256\",\"value\":\"" + digest + "\"}}";
        return new MockMultipartFile(
                "metadata", "metadata.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }
}
