package com.wwa.agenthub.web;

import com.jayway.jsonpath.JsonPath;
import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.integration.ArtifactUploadMetadata;
import com.wwa.agenthub.contracts.dto.integration.ReviewSubmissionRequest;
import com.wwa.agenthub.contracts.dto.integration.StartExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.SubmitExecutionRequest;
import com.wwa.agenthub.contracts.enums.ArtifactKind;
import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import com.wwa.agenthub.contracts.enums.IntegrationReviewDecisionType;
import com.wwa.agenthub.contracts.enums.PermissionKey;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.helper.TestDataHelper;
import com.wwa.agenthub.platform.domain.integration.event.ExecutionEventRepository;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactService;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactRepository;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.lifecycle.ExecutionLifecycleService;
import com.wwa.agenthub.platform.domain.integration.review.IntegrationReviewService;
import com.wwa.agenthub.domain.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class AtlasIntegrationControllerTest {

    private static final String TOKEN = "atlas-test-token-1234567890";

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataHelper helper;
    @Autowired private ExecutionEventRepository eventRepository;
    @Autowired private ExecutionLifecycleService lifecycleService;
    @Autowired private IntegrationArtifactService artifactService;
    @Autowired private IntegrationReviewService reviewService;
    @Autowired private IntegrationArtifactRepository artifactRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private Task task;

    @BeforeEach
    void setUp() {
        ReleaseFlow flow = helper.seedReleaseFlow();
        Request request = helper.seedRequest(flow);
        task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
    }

    @Test
    void bearerStartIsServerDerivedCorrelatedRedactedAndReplayable() throws Exception {
        String key = "start-atlas-00000001";
        MvcResult first = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "atlas-correlation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "atlas-correlation-1"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.client.clientType").value("COPILOT"))
                .andExpect(jsonPath("$.data.client.applicationId").value("atlas-copilot-test"))
                .andExpect(jsonPath("$.data.user.userId").value("alice"))
                .andExpect(jsonPath("$.data.pendingSync").value(true))
                .andExpect(jsonPath("$.data.inputSnapshot").doesNotExist())
                .andExpect(jsonPath("$.data.resultLogs").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andReturn();
        String executionId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.executionId");

        MvcResult replay = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn();
        assertThat(JsonPath.<String>read(replay.getResponse().getContentAsString(), "$.data.executionId"))
                .isEqualTo(executionId);
        assertThat(eventRepository.findByExecutionIdOrderByReceivedAtAsc(executionId))
                .singleElement()
                .satisfies(event -> assertThat(event.getCorrelationId()).isEqualTo("atlas-correlation-1"));

        mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("2.0.0")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void missingIdempotencyAndInvalidBearerUseIntegrationErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_IDEMPOTENCY_KEY"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());

        mockMvc.perform(get("/api/v1/integration/tasks")
                        .header("Authorization", "Bearer definitely-invalid-token")
                        .header("X-Correlation-Id", "atlas-auth-failure-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "atlas-auth-failure-1"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.error.requestId").value("atlas-auth-failure-1"));
    }

    @Test
    void crossAgentTaskProbeIsHidden() throws Exception {
        Request testingRequest = helper.seedRequest(
                task.getRequest().getReleaseFlow(),
                "SIT",
                com.wwa.agenthub.contracts.enums.RequestStatus.Pending,
                AgentId.TESTING_AGENT);
        Task testingTask = helper.seedIntegrationTask(
                testingRequest, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");

        mockMvc.perform(get("/api/v1/integration/tasks/{id}", testingTask.getId())
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void explicitBearerIdentityTakesPriorityOverAnExistingWebSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_CONTEXT", new UserContext("mallory", "DEVOPS_ADMIN"));

        mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .session(session)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "bearer-priority-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.userId").value("alice"))
                .andExpect(jsonPath("$.data.client.clientType").value("COPILOT"));
    }

    @Test
    void webSessionCannotIssueExecutionCommands() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_CONTEXT", new UserContext(
                "alice",
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(),
                "Alice",
                List.of(new AccessScope("*", "*"))));

        mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .session(session)
                        .header("Idempotency-Key", "web-start-forbidden-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void submitRequiresArtifactIdsEvenForManualExecution() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "required-artifacts-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated())
                .andReturn();
        String executionId = JsonPath.read(start.getResponse().getContentAsString(), "$.data.executionId");

        mockMvc.perform(post("/api/v1/integration/executions/{id}/submit", executionId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "required-artifacts-submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"manual complete\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void bearerCannotSubmitHumanReview() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "start-review-000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated())
                .andReturn();
        String executionId = JsonPath.read(start.getResponse().getContentAsString(), "$.data.executionId");

        mockMvc.perform(post("/api/v1/integration/executions/{id}/submit", executionId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "submit-review-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"manual complete\",\"artifactIds\":[]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/integration/executions/{id}/review-decision", executionId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "review-bearer-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void terminalCommandReplayReturnsStoredSuccessBeforeStateRejection() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "terminal-start-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated())
                .andReturn();
        String executionId = JsonPath.read(start.getResponse().getContentAsString(), "$.data.executionId");
        String submitBody = "{\"summary\":\"manual complete\",\"artifactIds\":[]}";

        mockMvc.perform(post("/api/v1/integration/executions/{id}/submit", executionId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "terminal-submit-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        mockMvc.perform(post("/api/v1/integration/executions/{id}/submit", executionId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "terminal-submit-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        mockMvc.perform(post("/api/v1/integration/executions/{id}/submit", executionId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "terminal-submit-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_ALREADY_FINALIZED"));
    }

    @Test
    void unknownServerOwnedRequestFieldsAreRejected() throws Exception {
        String valid = createExecutionBody("1.2.3");
        String body = valid.substring(0, valid.length() - 1) + ",\"status\":\"SUCCEEDED\"}";

        mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "unknown-field-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_JSON"));
    }

    @Test
    void secretLikeCorrelationIdIsReplacedBeforePersistenceOrDisplay() throws Exception {
        String secretLike = "AKIA1234567890ABCDEF";

        MvcResult result = mockMvc.perform(get("/api/v1/integration/tasks")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("X-Correlation-Id", secretLike))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-Id"))
                .isNotEqualTo(secretLike)
                .matches("[a-f0-9]{16}");
    }

    @Test
    void exactPresentedBearerCannotBecomeCorrelationId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "credential-correlation-0001")
                        .header("X-Correlation-Id", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated())
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        String executionId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.executionId");
        assertThat(correlationId).isNotEqualTo(TOKEN).matches("[a-f0-9]{16}");
        assertThat(JsonPath.<String>read(
                result.getResponse().getContentAsString(), "$.data.correlationId"))
                .isEqualTo(correlationId);
        assertThat(eventRepository.findByExecutionIdOrderByReceivedAtAsc(executionId))
                .allSatisfy(event -> assertThat(event.getCorrelationId()).isEqualTo(correlationId));
        assertThat(auditLogRepository.findAll())
                .filteredOn(entry -> task.getId().equals(entry.getTaskId()))
                .allSatisfy(entry -> assertThat(entry.getCorrelationId()).isEqualTo(correlationId));
    }

    @Test
    void adminBindingIsIdempotentButDoesNotBypassRequestReadiness() throws Exception {
        Task unbound = helper.seedTask(task.getRequest(), TaskStatus.Pending);
        MockHttpSession admin = session(new UserContext("admin", "DEVOPS_ADMIN"));
        String body = "{"
                + "\"assigneeUserId\":\"alice\","
                + "\"capability\":{"
                + "\"capabilityType\":\"MANUAL\","
                + "\"capabilityId\":\"capability.atlas.delivery\","
                + "\"capabilityVersion\":\"1.0.0\"}}";

        mockMvc.perform(put("/api/v1/integration/admin/tasks/{id}/binding", unbound.getId())
                        .session(admin)
                        .header("Idempotency-Key", "binding-command-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.assignee.userId").value("alice"))
                .andExpect(jsonPath("$.data.assignee.displayName").value("alice"));

        mockMvc.perform(put("/api/v1/integration/admin/tasks/{id}/binding", unbound.getId())
                        .session(admin)
                        .header("Idempotency-Key", "binding-command-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"));
    }

    @Test
    void integrationBoundaryRejectsUserIdsOutsideThePublishedResourceIdShape() throws Exception {
        Task unbound = helper.seedTask(task.getRequest(), TaskStatus.Pending);
        String binding = "{"
                + "\"assigneeUserId\":\"alice@example.com\","
                + "\"capability\":{"
                + "\"capabilityType\":\"MANUAL\","
                + "\"capabilityId\":\"capability.atlas.delivery\","
                + "\"capabilityVersion\":\"1.0.0\"}}";

        mockMvc.perform(put("/api/v1/integration/admin/tasks/{id}/binding", unbound.getId())
                        .session(session(new UserContext("admin", "DEVOPS_ADMIN")))
                        .header("Idempotency-Key", "binding-invalid-user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(binding))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/integration/tasks")
                        .session(session(new UserContext("alice@example.com", "DEVOPS_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ACTOR_IDENTITY"));
    }

    @Test
    void bindingRejectsPercentEncodedSshCredentials() throws Exception {
        Task unbound = helper.seedTask(task.getRequest(), TaskStatus.Pending);
        String body = "{"
                + "\"assigneeUserId\":\"alice\","
                + "\"capability\":{"
                + "\"capabilityType\":\"SKILL\","
                + "\"capabilityId\":\"skill.atlas.delivery\","
                + "\"capabilityVersion\":\"1.0.0\"},"
                + "\"repository\":{"
                + "\"repositoryId\":\"repo-atlas-001\","
                + "\"url\":\"ssh://git%3Apassword@example.com/repo.git\","
                + "\"provider\":\"GITHUB\","
                + "\"branch\":\"main\","
                + "\"commit\":\"abcdef1234567890abcdef1234567890abcdef12\"}}";

        mockMvc.perform(put("/api/v1/integration/admin/tasks/{id}/binding", unbound.getId())
                        .session(session(new UserContext("admin", "DEVOPS_ADMIN")))
                        .header("Idempotency-Key", "binding-unsafe-url-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_TASK_BINDING"));
    }

    @Test
    void rerunRequiresHumanAndTheExactLatestExecution() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "rerun-start-000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated())
                .andReturn();
        String executionId = JsonPath.read(start.getResponse().getContentAsString(), "$.data.executionId");
        mockMvc.perform(post("/api/v1/integration/executions/{id}/fail", executionId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "rerun-fail-0000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReason\":{\"code\":\"TEST_FAILURE\","
                                + "\"message\":\"failed\",\"retryable\":true}}"))
                .andExpect(status().isOk());

        String rerunBody = "{\"executionId\":\"" + executionId + "\"}";
        mockMvc.perform(post("/api/v1/integration/tasks/{id}/rerun", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "rerun-bearer-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rerunBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/integration/tasks/{id}/rerun", task.getId())
                        .session(session(scopedUser("alice", Set.of())))
                        .header("Idempotency-Key", "rerun-human-000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rerunBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_EXECUTION"));

        mockMvc.perform(post("/api/v1/integration/tasks/{id}/executions", task.getId())
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "rerun-start-000002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExecutionBody("1.2.3")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/integration/executions/{id}/fail", executionId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "rerun-fail-0000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureReason\":{\"code\":\"TEST_FAILURE\","
                                + "\"message\":\"failed\",\"retryable\":true}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_EXECUTION"));
    }

    @Test
    void approvedInputReplayReauthorizesCurrentPermission() throws Exception {
        Request request = task.getRequest();
        Task source = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.SKILL, "approver");
        Task target = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "approver");
        IntegrationActor client = integrationActor("approver", "prep-client", true, Set.of());
        var running = lifecycleService.start(source.getId(), startRequest(source), client);
        byte[] content = "verified evidence".getBytes(StandardCharsets.UTF_8);
        var artifact = artifactService.upload(
                running.executionId(),
                new ArtifactUploadMetadata(
                        ArtifactRole.EVIDENCE,
                        ArtifactKind.TEXT,
                        "evidence.txt",
                        "text/plain",
                        content.length,
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                        "reports/evidence.txt"),
                content,
                client);
        lifecycleService.submit(
                running.executionId(),
                new SubmitExecutionRequest("complete", List.of(artifact.artifactId())),
                client);
        reviewService.submit(
                running.executionId(),
                new ReviewSubmissionRequest(IntegrationReviewDecisionType.APPROVED, "approved"),
                integrationActor("approver", "atlas-web", false, Set.of()));

        String path = "/api/v1/integration/admin/tasks/" + target.getId()
                + "/approved-input-artifacts/" + artifact.artifactId();
        MockHttpSession authorized = session(scopedUser(
                "approver", Set.of(PermissionKey.PLATFORM_ACCESS_MANAGE.value())));
        mockMvc.perform(post(path)
                        .session(authorized)
                        .header("Idempotency-Key", "approve-input-000001"))
                .andExpect(status().isOk());
        assertThat(artifactRepository.findWithProvenance(artifact.artifactId()).orElseThrow()
                .isLegalHold()).isTrue();

        mockMvc.perform(post(path)
                        .session(session(scopedUser("approver", Set.of())))
                        .header("Idempotency-Key", "approve-input-000001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private static MockHttpSession session(UserContext user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_CONTEXT", user);
        return session;
    }

    private static UserContext scopedUser(String userId, Set<String> permissions) {
        return new UserContext(
                userId,
                "DEVELOPER",
                List.of("DEVELOPER"),
                permissions,
                userId,
                List.of(new AccessScope("*", "*")));
    }

    private IntegrationActor integrationActor(
            String userId,
            String applicationId,
            boolean bearer,
            Set<String> permissions
    ) {
        return new IntegrationActor(
                scopedUser(userId, permissions),
                applicationId,
                IntegrationClientType.MANUAL,
                "1.0",
                Set.of(task.getRequest().getAgent()),
                bearer);
    }

    private static StartExecutionRequest startRequest(Task task) {
        return new StartExecutionRequest(
                "1.0",
                new StartExecutionRequest.Capability(
                        task.getCapabilityId(), task.getCapabilityType(), task.getCapabilityVersion()),
                new StartExecutionRequest.ProjectContext(
                        task.getRequest().getReleaseFlow().getProjectId(),
                        task.getRepositoryId(), task.getRepositoryBranch(), task.getRepositoryCommit()));
    }

    private static String createExecutionBody(String clientVersion) {
        return "{"
                + "\"clientVersion\":\"" + clientVersion + "\","
                + "\"capability\":{"
                + "\"capabilityId\":\"capability.atlas.delivery\","
                + "\"capabilityType\":\"MANUAL\","
                + "\"capabilityVersion\":\"1.0.0\"},"
                + "\"projectContext\":{\"projectId\":\"PROJ-001\"}}";
    }
}
