package com.wwa.agenthub.platform.integration;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.integration.ArtifactUploadMetadata;
import com.wwa.agenthub.contracts.dto.integration.CancelExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.CapabilityUsageDto;
import com.wwa.agenthub.contracts.dto.integration.FailExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.IntegrationArtifactDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationExecutionDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationTaskBindingRequest;
import com.wwa.agenthub.contracts.dto.integration.IntegrationTaskDto;
import com.wwa.agenthub.contracts.dto.integration.ProgressEventRequest;
import com.wwa.agenthub.contracts.dto.integration.RerunTaskRequest;
import com.wwa.agenthub.contracts.dto.integration.ReviewSubmissionRequest;
import com.wwa.agenthub.contracts.dto.RequestRundownUpdateDto;
import com.wwa.agenthub.contracts.dto.integration.StartExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.SubmitExecutionRequest;
import com.wwa.agenthub.contracts.enums.ArtifactKind;
import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.FlowStatus;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import com.wwa.agenthub.contracts.enums.IntegrationExecutionStatus;
import com.wwa.agenthub.contracts.enums.IntegrationReviewDecisionType;
import com.wwa.agenthub.contracts.enums.PermissionKey;
import com.wwa.agenthub.contracts.enums.RequestStatus;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.decision.DecisionEngine;
import com.wwa.agenthub.domain.audit.AuditLogRepository;
import com.wwa.agenthub.domain.decision.DecisionType;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowService;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.agenthub.domain.releaseflow.RequestRepository;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryService;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.domain.task.TaskService;
import com.wwa.agenthub.helper.TestDataHelper;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactService;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactRepository;
import com.wwa.agenthub.platform.domain.integration.artifact.ArtifactRetentionService;
import com.wwa.agenthub.platform.domain.integration.binding.IntegrationTaskBindingService;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientProperties;
import com.wwa.agenthub.platform.domain.integration.auth.PresentedCredentialLeakGuard;
import com.wwa.agenthub.platform.domain.integration.event.ExecutionEventRepository;
import com.wwa.agenthub.platform.domain.integration.lifecycle.ExecutionLifecycleService;
import com.wwa.agenthub.platform.domain.integration.review.IntegrationReviewService;
import com.wwa.agenthub.platform.domain.integration.telemetry.CapabilityUsageService;
import com.wwa.agenthub.errors.ConflictAppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExecutionLifecycleServiceTest {

    @Autowired private TestDataHelper helper;
    @Autowired private ExecutionLifecycleService lifecycleService;
    @Autowired private IntegrationArtifactService artifactService;
    @Autowired private IntegrationTaskBindingService taskBindingService;
    @Autowired private IntegrationReviewService reviewService;
    @Autowired private CapabilityUsageService capabilityUsageService;
    @Autowired private TaskService taskService;
    @Autowired private ReleaseFlowService releaseFlowService;
    @Autowired private DecisionEngine decisionEngine;
    @Autowired private TaskRepository taskRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private ReleaseFlowRepository releaseFlowRepository;
    @Autowired private TaskExecutionHistoryRepository executionRepository;
    @Autowired private TaskExecutionHistoryService executionHistoryService;
    @Autowired private ExecutionEventRepository eventRepository;
    @Autowired private IntegrationClientProperties integrationProperties;
    @Autowired private IntegrationArtifactRepository artifactRepository;
    @Autowired private ArtifactRetentionService artifactRetentionService;
    @Autowired private PresentedCredentialLeakGuard credentialLeakGuard;
    @Autowired private AuditLogRepository auditLogRepository;

    private Request request;
    private IntegrationActor copilotActor;

    @BeforeEach
    void setUp() {
        ReleaseFlow flow = helper.seedReleaseFlow();
        request = helper.seedRequest(flow);
        copilotActor = actor("alice", "copilot-client", IntegrationClientType.COPILOT, true);
    }

    @Test
    void startCreatesOneRunningAttemptAndTaskFence() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.SKILL, "alice");

        IntegrationExecutionDto execution = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);

        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(execution.status()).isEqualTo(IntegrationExecutionStatus.RUNNING);
        assertThat(execution.attemptNumber()).isEqualTo(1);
        assertThat(execution.pendingSync()).isTrue();
        assertThat(persisted.getTaskStatus()).isEqualTo(TaskStatus.Executing);
        assertThat(persisted.getActiveExecutionId()).isEqualTo(execution.executionId());
        assertThat(persisted.getLatestExecutionId()).isEqualTo(execution.executionId());
        assertThat(eventRepository.findByExecutionIdOrderByReceivedAtAsc(execution.executionId()))
                .hasSize(1);

        assertThatThrownBy(() -> lifecycleService.start(
                task.getId(), startRequest(task), copilotActor))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ACTIVE_EXECUTION_EXISTS"));
    }

    @Test
    void startRejectsSecretMaterialInClientVersionWithoutCreatingAttempt() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        StartExecutionRequest valid = startRequest(task);
        String presentedToken = "atlas-test-token-1234567890";
        StartExecutionRequest unsafe = new StartExecutionRequest(
                presentedToken,
                valid.capability(),
                valid.projectContext());

        try (var ignored = credentialLeakGuard.bind(presentedToken)) {
            assertThatThrownBy(() -> lifecycleService.start(task.getId(), unsafe, copilotActor))
                    .isInstanceOfSatisfying(IntegrationApiException.class,
                            error -> assertThat(error.getCode()).isEqualTo("INVALID_REQUEST"));
        }
        assertThat(executionRepository.findByTaskIdOrderByAttemptNumberAsc(task.getId())).isEmpty();
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTaskStatus())
                .isEqualTo(TaskStatus.Ready_For_Execution);
    }

    @Test
    void terminalCommandRejectsTheExactPresentedBearerTokenWithoutMutation() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationExecutionDto running = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);
        String presentedToken = "atlas-test-token-1234567890";

        try (var ignored = credentialLeakGuard.bind(presentedToken)) {
            assertThatThrownBy(() -> lifecycleService.fail(
                    running.executionId(),
                    new FailExecutionRequest("CLIENT_FAILURE", presentedToken, false),
                    copilotActor))
                    .isInstanceOfSatisfying(IntegrationApiException.class,
                            error -> assertThat(error.getCode()).isEqualTo("VALIDATION_FAILED"));
        }

        assertThat(executionRepository.findById(running.executionId()).orElseThrow()
                .getExecutionStatus()).isEqualTo(com.wwa.agenthub.contracts.enums.ExecutionStatus.Running);
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTaskStatus())
                .isEqualTo(TaskStatus.Executing);
    }

    @Test
    void staleExecutionAndCrossClientWritesAreRejected() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationExecutionDto execution = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);

        IntegrationActor otherClient = actor("alice", "kiro-client", IntegrationClientType.KIRO, true);
        assertThatThrownBy(() -> lifecycleService.progress(
                execution.executionId(),
                new ProgressEventRequest(1L, 10, "working", Instant.now()),
                otherClient))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("FORBIDDEN"));

        assertThatThrownBy(() -> lifecycleService.cancel(
                execution.executionId(),
                new CancelExecutionRequest("int main(){return 0;}"),
                copilotActor))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("VALIDATION_FAILED"));

        IntegrationExecutionDto cancelled = lifecycleService.cancel(
                execution.executionId(),
                new CancelExecutionRequest("Operator stopped the run"),
                copilotActor);
        assertThat(cancelled.status()).isEqualTo(IntegrationExecutionStatus.CANCELLED);
        assertThat(cancelled.cancellationReason())
                .isEqualTo("Operator stopped the run");

        assertThatThrownBy(() -> lifecycleService.progress(
                execution.executionId(),
                new ProgressEventRequest(2L, 20, "late", Instant.now()),
                copilotActor))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("EXECUTION_ALREADY_FINALIZED"));
    }

    @Test
    void explicitlyDelegatedRegisteredClientCanWriteScopedExecution() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationExecutionDto execution = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);
        UserContext delegateUser = new UserContext(
                "delegate",
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(PermissionKey.PLATFORM_EXECUTION_DELEGATE.value()),
                "Delegate",
                List.of(new AccessScope("*", "*")));
        IntegrationActor delegate = new IntegrationActor(
                delegateUser,
                "pipeline-delegate",
                IntegrationClientType.PIPELINE,
                "1.0",
                Set.of(request.getAgent()),
                true);

        assertThatThrownBy(() -> lifecycleService.progress(
                execution.executionId(),
                new ProgressEventRequest(1L, 10, "print(\"hello\")", Instant.now()),
                delegate))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("VALIDATION_FAILED"));

        var progress = lifecycleService.progress(
                execution.executionId(),
                new ProgressEventRequest(1L, 10, "Tests started", Instant.now()),
                delegate);
        assertThat(progress.percent()).isEqualTo(10);
        assertThat(progress.message()).isEqualTo("Tests started");
    }

    @Test
    void artifactSubmitAndHumanReviewPreserveSeparateOutcomes() throws Exception {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.SKILL, "alice");
        IntegrationExecutionDto running = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);
        byte[] content = "tests passed".getBytes(StandardCharsets.UTF_8);
        IntegrationArtifactDto artifact = artifactService.upload(
                running.executionId(),
                new ArtifactUploadMetadata(
                        ArtifactRole.EVIDENCE,
                        ArtifactKind.TEXT,
                        "test-summary.txt",
                        "text/plain",
                        content.length,
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                        "reports/test-summary.txt"),
                content,
                copilotActor);

        IntegrationExecutionDto submitted = lifecycleService.submit(
                running.executionId(),
                new SubmitExecutionRequest("safe summary", List.of(artifact.artifactId())),
                copilotActor);
        assertThat(submitted.status()).isEqualTo(IntegrationExecutionStatus.SUCCEEDED);
        assertThat(submitted.artifactCount()).isEqualTo(1);
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTaskStatus())
                .isEqualTo(TaskStatus.Awaiting_Review);
        assertThat(executionRepository.findById(running.executionId()).orElseThrow().getResultSummary())
                .containsEntry("summary", "safe summary");

        IntegrationActor human = actor("alice", "atlas-web", IntegrationClientType.MANUAL, false);
        assertThatThrownBy(() -> reviewService.submit(
                running.executionId(),
                new ReviewSubmissionRequest(
                        IntegrationReviewDecisionType.APPROVED,
                        "return credentials;"),
                human))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("VALIDATION_FAILED"));

        var review = reviewService.submit(
                running.executionId(),
                new ReviewSubmissionRequest(
                        IntegrationReviewDecisionType.APPROVED,
                        "Evidence verified and approved"),
                human);

        assertThat(review.decision()).isEqualTo(IntegrationReviewDecisionType.APPROVED);
        assertThat(review.comment()).isEqualTo("Evidence verified and approved");
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTaskStatus())
                .isEqualTo(TaskStatus.Approved);
        assertThat(executionRepository.findById(running.executionId()).orElseThrow().getExecutionStatus().name())
                .isEqualTo("Completed");
    }

    @Test
    void nonManualSubmitRequiresArtifactAndSafeFailureFeedsTelemetry() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.SKILL, "alice");
        IntegrationExecutionDto running = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);

        assertThatThrownBy(() -> lifecycleService.submit(
                running.executionId(), new SubmitExecutionRequest("done", List.of()), copilotActor))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REQUIRED_ARTIFACTS_MISSING"));

        assertThatThrownBy(() -> lifecycleService.fail(
                running.executionId(),
                new FailExecutionRequest(
                        "TEST_FAILURE",
                        "Tests failed; token=super-secret-value",
                        true),
                copilotActor))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("VALIDATION_FAILED"));

        IntegrationExecutionDto failed = lifecycleService.fail(
                running.executionId(),
                new FailExecutionRequest(
                        "TEST_FAILURE",
                        "Tests failed while publishing evidence",
                        true),
                copilotActor);

        assertThat(failed.failureReason().message())
                .isEqualTo("Tests failed while publishing evidence");

        IntegrationActor manager = actor("manager", "atlas-web", IntegrationClientType.MANUAL, false,
                "MANAGEMENT");
        CapabilityUsageDto usage = capabilityUsageService.aggregate(
                new CapabilityUsageDto.Filters(
                        null,
                        "skill.atlas.delivery",
                        "team-atlas",
                        "PROJ-001",
                        request.getAgent(),
                        LocalDate.now().minusDays(1),
                        LocalDate.now().plusDays(1),
                        IntegrationClientType.COPILOT),
                manager);

        assertThat(usage.items()).singleElement().satisfies(row -> {
            assertThat(row.invocationCount()).isEqualTo(1);
            assertThat(row.failureCount()).isEqualTo(1);
            assertThat(row.failureRate()).isEqualTo(100.0);
            assertThat(row.skillId()).isEqualTo("skill.atlas.delivery");
            assertThat(row.userCount()).isEqualTo(1);
            assertThat(row.versionDistribution()).singleElement()
                    .satisfies(version -> assertThat(version.version()).isEqualTo("1.0.0"));
        });
    }

    @Test
    void telemetrySupportsEveryRequiredClientType() {
        IntegrationActor manager = actor(
                "manager", "atlas-web", IntegrationClientType.MANUAL, false, "MANAGEMENT");

        for (IntegrationClientType clientType : IntegrationClientType.values()) {
            Task task = helper.seedIntegrationTask(
                    request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
            IntegrationActor client = actor(
                    "alice", "client-" + clientType.name().toLowerCase(), clientType, true);
            IntegrationExecutionDto running = lifecycleService.start(
                    task.getId(), startRequest(task), client);
            lifecycleService.submit(
                    running.executionId(), new SubmitExecutionRequest("complete", List.of()), client);

            CapabilityUsageDto usage = capabilityUsageService.aggregate(
                    new CapabilityUsageDto.Filters(
                            "capability.atlas.delivery",
                            null,
                            "team-atlas",
                            "PROJ-001",
                            request.getAgent(),
                            LocalDate.now().minusDays(1),
                            LocalDate.now().plusDays(1),
                            clientType),
                    manager);
            assertThat(usage.items()).singleElement().satisfies(row -> {
                assertThat(row.invocationCount()).isEqualTo(1);
                assertThat(row.successCount()).isEqualTo(1);
                assertThat(row.successRate()).isEqualTo(100.0);
            });
        }
    }

    @Test
    void telemetryAggregatesUsersOutcomesAndSkillVersionDistribution() throws Exception {
        IntegrationActor alice = actor(
                "alice", "copilot-v1", IntegrationClientType.COPILOT, true);
        Task first = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.SKILL, "alice");
        first.setCapabilityVersion("1.0.0");
        taskRepository.save(first);
        IntegrationExecutionDto firstRun = lifecycleService.start(
                first.getId(), startRequest(first), alice);
        lifecycleService.fail(
                firstRun.executionId(),
                new FailExecutionRequest("TEST_FAILURE", "Tests failed", false),
                alice);

        IntegrationActor bob = actor(
                "bob", "kiro-v2", IntegrationClientType.KIRO, true);
        Task second = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.SKILL, "bob");
        second.setCapabilityVersion("2.0.0");
        taskRepository.save(second);
        IntegrationExecutionDto secondRun = lifecycleService.start(
                second.getId(), startRequest(second), bob);
        byte[] report = "tests passed".getBytes(StandardCharsets.UTF_8);
        IntegrationArtifactDto evidence = artifactService.upload(
                secondRun.executionId(), artifactMetadata("tests.txt", report), report, bob);
        lifecycleService.submit(
                secondRun.executionId(),
                new SubmitExecutionRequest("complete", List.of(evidence.artifactId())),
                bob);

        IntegrationActor manager = actor(
                "manager", "atlas-web", IntegrationClientType.MANUAL, false, "MANAGEMENT");
        CapabilityUsageDto usage = capabilityUsageService.aggregate(
                new CapabilityUsageDto.Filters(
                        null,
                        "skill.atlas.delivery",
                        "team-atlas",
                        "PROJ-001",
                        request.getAgent(),
                        LocalDate.now().minusDays(1),
                        LocalDate.now().plusDays(1),
                        null),
                manager);

        assertThat(usage.items()).singleElement().satisfies(row -> {
            assertThat(row.invocationCount()).isEqualTo(2);
            assertThat(row.successCount()).isEqualTo(1);
            assertThat(row.failureCount()).isEqualTo(1);
            assertThat(row.successRate()).isEqualTo(50.0);
            assertThat(row.failureRate()).isEqualTo(50.0);
            assertThat(row.userCount()).isEqualTo(2);
            assertThat(row.averageDurationMs()).isGreaterThanOrEqualTo(0.0);
            assertThat(row.versionDistribution())
                    .extracting(CapabilityUsageDto.Version::version)
                    .containsExactlyInAnyOrder("1.0.0", "2.0.0");
        });
    }

    @Test
    void integrationBoundTaskCannotUseLegacyManualStart() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");

        assertThatThrownBy(() -> taskService.startManualExecution(task.getId(), copilotActor.user()))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("Atlas Integration Execution API");
    }

    @Test
    void historicalExecutionAuthorizationUsesImmutableScopeSnapshots() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        request.setApplication("application-old");
        request.setSnowGroup("team-old");
        IntegrationActor originalScopeClient = scopedActor(
                "alice", "application-old", "team-old", true);
        IntegrationExecutionDto started = lifecycleService.start(
                task.getId(), startRequest(task), originalScopeClient);

        request.setApplication("application-new");
        request.setSnowGroup("team-new");

        IntegrationExecutionDto historical = lifecycleService.getExecution(
                started.executionId(), originalScopeClient);
        assertThat(historical.projectContext().team()).isEqualTo("team-old");
        assertThat(historical.projectContext().agentModuleId()).isEqualTo(request.getAgent());

        IntegrationActor movedScopeClient = scopedActor(
                "alice", "application-new", "team-new", true);
        assertThatThrownBy(() -> lifecycleService.getExecution(
                started.executionId(), movedScopeClient))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("EXECUTION_NOT_FOUND"));

        assertThat(executionHistoryService.findByTaskId(task.getId())).isEmpty();
    }

    @Test
    void legacyRequestCommandsCannotBypassIntegrationScopeOrActiveFence() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        lifecycleService.start(task.getId(), startRequest(task), copilotActor);

        assertThatThrownBy(() -> releaseFlowService.markRequestFailed(
                request.getReleaseFlow().getId(), request.getId(), copilotActor.user()))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("Atlas Execution or Review commands");
        assertThatThrownBy(() -> releaseFlowService.archiveRequestRundown(
                request.getReleaseFlow().getId(), request.getId(), copilotActor.user()))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("active Atlas Integration Execution");
        assertThatThrownBy(() -> releaseFlowService.updateRequestRundown(
                request.getReleaseFlow().getId(),
                request.getId(),
                new RequestRundownUpdateDto(
                        "another-team",
                        request.getApplication(),
                        request.getAgent(),
                        null,
                        null,
                        null)))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("cannot change after Atlas Integration binding");
    }

    @Test
    void integrationBoundReviewCannotBypassExactExecutionReviewResource() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Awaiting_Review, CapabilityType.MANUAL, "alice");

        assertThatThrownBy(() -> decisionEngine.applyDecision(
                task.getId(), DecisionType.approve, copilotActor.user(), "bypass"))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("exact Execution review endpoint");
    }

    @Test
    void explicitReviewPermissionCanReviewLatestSuccessfulAttempt() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationExecutionDto running = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);
        lifecycleService.submit(
                running.executionId(), new SubmitExecutionRequest("complete", List.of()), copilotActor);

        UserContext reviewerUser = new UserContext(
                "reviewer",
                "TL",
                List.of("TL"),
                Set.of(PermissionKey.PLATFORM_EXECUTION_REVIEW.value()),
                "Reviewer",
                List.of(new AccessScope("*", "*")));
        IntegrationActor reviewer = new IntegrationActor(
                reviewerUser,
                "atlas-web",
                IntegrationClientType.MANUAL,
                "1.0",
                Set.of(request.getAgent()),
                false);

        var review = reviewService.submit(
                running.executionId(),
                new ReviewSubmissionRequest(IntegrationReviewDecisionType.APPROVED, "reviewed"),
                reviewer);

        assertThat(review.reviewer().userId()).isEqualTo("reviewer");
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTaskStatus())
                .isEqualTo(TaskStatus.Approved);
    }

    @Test
    void bindingWritesMetadataWhileRequestLifecycleOwnsReadiness() {
        request.setApplication("payments");
        request.setSnowGroup("team-atlas");
        requestRepository.save(request);
        Task unbound = helper.seedTask(request, TaskStatus.Pending);
        IntegrationActor admin = new IntegrationActor(
                new UserContext("admin", "DEVOPS_ADMIN"),
                "atlas-web",
                IntegrationClientType.MANUAL,
                "1.0",
                Set.of(request.getAgent()),
                false);

        IntegrationTaskDto bound = taskBindingService.bind(
                unbound.getId(),
                new IntegrationTaskBindingRequest(
                        "alice",
                        new IntegrationTaskBindingRequest.Capability(
                                CapabilityType.MANUAL,
                                "capability.atlas.delivery",
                                "1.0.0"),
                        null),
                admin);

        assertThat(bound.status()).isEqualTo("PENDING");
        assertThat(taskRepository.findById(unbound.getId()).orElseThrow().getTaskStatus())
                .isEqualTo(TaskStatus.Pending);
        releaseFlowService.startRequestDeployment(
                request.getReleaseFlow().getId(), request.getId(), admin.user());
        assertThat(taskRepository.findById(unbound.getId()).orElseThrow().getTaskStatus())
                .isEqualTo(TaskStatus.Ready_For_Execution);
    }

    @Test
    void bindingRejectsRenderedSecretLikeMetadataWithoutMutation() {
        request.setApplication("payments");
        request.setSnowGroup("team-atlas");
        requestRepository.save(request);
        Task unbound = helper.seedTask(request, TaskStatus.Pending);
        IntegrationActor admin = new IntegrationActor(
                new UserContext("admin", "DEVOPS_ADMIN"),
                "atlas-web",
                IntegrationClientType.MANUAL,
                "1.0",
                Set.of(request.getAgent()),
                false);
        long auditCount = auditLogRepository.count();

        assertThatThrownBy(() -> taskBindingService.bind(
                unbound.getId(),
                new IntegrationTaskBindingRequest(
                        "alice",
                        new IntegrationTaskBindingRequest.Capability(
                                CapabilityType.MANUAL,
                                "ghp_abcdefghijklmnopqrstuvwxyz123456",
                                "1.0.0"),
                        null),
                admin))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_TASK_BINDING"));

        Task persisted = taskRepository.findById(unbound.getId()).orElseThrow();
        assertThat(persisted.getCapabilityId()).isNull();
        assertThat(persisted.getCapabilityVersion()).isNull();
        assertThat(persisted.getTaskStatus()).isEqualTo(TaskStatus.Pending);
        assertThat(auditLogRepository.count()).isEqualTo(auditCount);
    }

    @Test
    void lifecycleRecomputesParentStatusAndRerunIsHumanOnly() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationExecutionDto running = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);
        lifecycleService.fail(
                running.executionId(),
                new FailExecutionRequest("TEST_FAILURE", "failed", true),
                copilotActor);

        assertThat(requestRepository.findById(request.getId()).orElseThrow().getRequestStatus())
                .isEqualTo(RequestStatus.Failed);
        assertThat(releaseFlowRepository.findById(request.getReleaseFlow().getId())
                .orElseThrow().getFlowStatus()).isEqualTo(FlowStatus.Failed);
        assertThatThrownBy(() -> lifecycleService.rerun(
                task.getId(), new RerunTaskRequest(running.executionId()), copilotActor))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("FORBIDDEN"));

        IntegrationActor human = actor("alice", "atlas-web", IntegrationClientType.MANUAL, false);
        lifecycleService.rerun(
                task.getId(), new RerunTaskRequest(running.executionId()), human);
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getRequestStatus())
                .isEqualTo(RequestStatus.Running);
        assertThat(releaseFlowRepository.findById(request.getReleaseFlow().getId())
                .orElseThrow().getFlowStatus()).isEqualTo(FlowStatus.Running);
    }

    @Test
    void taskCursorRemainsStableWhenEarlierTaskIsUpdatedBetweenPages() {
        List<Task> tasks = java.util.stream.IntStream.range(0, 3)
                .mapToObj(index -> helper.seedIntegrationTask(
                        request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice"))
                .toList();
        Set<String> expected = tasks.stream().map(Task::getId).collect(
                java.util.stream.Collectors.toSet());

        ExecutionLifecycleService.TaskWindow page = lifecycleService.listTasks(
                copilotActor,
                new ExecutionLifecycleService.TaskFilters(null, null, null, null),
                null,
                1);
        Set<String> seen = new java.util.LinkedHashSet<>();
        seen.add(page.items().getFirst().taskId());
        Task first = taskRepository.findById(page.items().getFirst().taskId()).orElseThrow();
        first.setTaskName("updated-between-pages");
        taskRepository.saveAndFlush(first);

        while (page.hasMore()) {
            page = lifecycleService.listTasks(
                    copilotActor,
                    new ExecutionLifecycleService.TaskFilters(null, null, null, null),
                    page.nextCursor(),
                    1);
            page.items().forEach(item -> assertThat(seen.add(item.taskId())).isTrue());
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void executionHistoryCursorIsStableWhenANewerAttemptStartsBetweenPages() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationActor human = actor(
                "alice", "atlas-web", IntegrationClientType.MANUAL, false);

        IntegrationExecutionDto first = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);
        lifecycleService.fail(
                first.executionId(), new FailExecutionRequest("FAILED_ONE", "failed one", true), copilotActor);
        lifecycleService.rerun(task.getId(), new RerunTaskRequest(first.executionId()), human);
        IntegrationExecutionDto second = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);
        lifecycleService.fail(
                second.executionId(), new FailExecutionRequest("FAILED_TWO", "failed two", true), copilotActor);
        lifecycleService.rerun(task.getId(), new RerunTaskRequest(second.executionId()), human);
        IntegrationExecutionDto third = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);

        ExecutionLifecycleService.ExecutionWindow firstPage = lifecycleService.history(
                task.getId(), copilotActor, null, 2);
        assertThat(firstPage.items())
                .extracting(IntegrationExecutionDto::attemptNumber)
                .containsExactly(3, 2);
        assertThat(firstPage.hasMore()).isTrue();

        lifecycleService.fail(
                third.executionId(), new FailExecutionRequest("FAILED_THREE", "failed three", true), copilotActor);
        lifecycleService.rerun(task.getId(), new RerunTaskRequest(third.executionId()), human);
        lifecycleService.start(task.getId(), startRequest(task), copilotActor);

        ExecutionLifecycleService.ExecutionWindow secondPage = lifecycleService.history(
                task.getId(), copilotActor, firstPage.nextCursor(), 2);
        assertThat(secondPage.items())
                .extracting(IntegrationExecutionDto::attemptNumber)
                .containsExactly(1);
        assertThat(secondPage.hasMore()).isFalse();
    }

    @Test
    void progressAttemptsAndArtifactsHaveCumulativeQuotas() throws Exception {
        int progressLimit = integrationProperties.getMaxProgressEventsPerExecution();
        int attemptLimit = integrationProperties.getMaxExecutionsPerTask();
        int artifactLimit = integrationProperties.getMaxArtifactsPerTask();
        integrationProperties.setMaxProgressEventsPerExecution(1);
        integrationProperties.setMaxExecutionsPerTask(1);
        integrationProperties.setMaxArtifactsPerTask(1);
        try {
            Task task = helper.seedIntegrationTask(
                    request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
            IntegrationExecutionDto running = lifecycleService.start(
                    task.getId(), startRequest(task), copilotActor);
            lifecycleService.progress(
                    running.executionId(),
                    new ProgressEventRequest(1L, 10, "working", Instant.now()),
                    copilotActor);
            assertThatThrownBy(() -> lifecycleService.progress(
                    running.executionId(),
                    new ProgressEventRequest(2L, 20, "still working", Instant.now()),
                    copilotActor))
                    .isInstanceOfSatisfying(IntegrationApiException.class,
                            error -> assertThat(error.getCode())
                                    .isEqualTo("PROGRESS_EVENT_QUOTA_EXCEEDED"));

            byte[] firstContent = "first report".getBytes(StandardCharsets.UTF_8);
            artifactService.upload(
                    running.executionId(),
                    artifactMetadata("first.txt", firstContent),
                    firstContent,
                    copilotActor);
            byte[] secondContent = "second report".getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> artifactService.upload(
                    running.executionId(),
                    artifactMetadata("second.txt", secondContent),
                    secondContent,
                    copilotActor))
                    .isInstanceOfSatisfying(IntegrationApiException.class,
                            error -> assertThat(error.getCode())
                                    .isEqualTo("ARTIFACT_TASK_QUOTA_EXCEEDED"));

            lifecycleService.cancel(
                    running.executionId(), new CancelExecutionRequest("operator stop"), copilotActor);
            assertThatThrownBy(() -> lifecycleService.start(
                    task.getId(), startRequest(task), copilotActor))
                    .isInstanceOfSatisfying(IntegrationApiException.class,
                            error -> assertThat(error.getCode())
                                    .isEqualTo("EXECUTION_ATTEMPT_QUOTA_EXCEEDED"));
        } finally {
            integrationProperties.setMaxProgressEventsPerExecution(progressLimit);
            integrationProperties.setMaxExecutionsPerTask(attemptLimit);
            integrationProperties.setMaxArtifactsPerTask(artifactLimit);
        }
    }

    @Test
    void rejectsSecretFailureCodesAndArtifactMetadataBeforePersistence() throws Exception {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationExecutionDto running = lifecycleService.start(
                task.getId(), startRequest(task), copilotActor);

        assertThatThrownBy(() -> lifecycleService.fail(
                running.executionId(),
                new FailExecutionRequest("AKIA1234567890ABCDEF", "failed", false),
                copilotActor))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("VALIDATION_FAILED"));

        byte[] content = "safe report".getBytes(StandardCharsets.UTF_8);
        ArtifactUploadMetadata metadata = new ArtifactUploadMetadata(
                ArtifactRole.EVIDENCE,
                "token=super-secret-value",
                "report.txt",
                "text/plain",
                content.length,
                new ArtifactUploadMetadata.Digest("SHA-256", HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(content))),
                "reports/report.txt");
        assertThatThrownBy(() -> artifactService.upload(
                running.executionId(), metadata, content, copilotActor))
                .isInstanceOfSatisfying(IntegrationApiException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo("ARTIFACT_POLICY_VIOLATION"));

        String presentedToken = "atlas-test-token-1234567890";
        ArtifactUploadMetadata credentialKind = new ArtifactUploadMetadata(
                ArtifactRole.EVIDENCE,
                presentedToken,
                "report.txt",
                "text/plain",
                content.length,
                new ArtifactUploadMetadata.Digest("SHA-256", HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(content))),
                "reports/report.txt");
        long artifactCount = artifactRepository.count();
        long eventCount = eventRepository.count();
        long auditCount = auditLogRepository.count();
        try (var ignored = credentialLeakGuard.bind(presentedToken)) {
            assertThatThrownBy(() -> artifactService.upload(
                    running.executionId(), credentialKind, content, copilotActor))
                    .isInstanceOfSatisfying(IntegrationApiException.class,
                            error -> assertThat(error.getCode())
                                    .isEqualTo("ARTIFACT_SECURITY_POLICY_VIOLATION"));
        }
        assertThat(artifactRepository.count()).isEqualTo(artifactCount);
        assertThat(eventRepository.count()).isEqualTo(eventCount);
        assertThat(auditLogRepository.count()).isEqualTo(auditCount);
    }

    @Test
    void referenceRenewsSourceRetentionWithoutPermanentLegalHold() throws Exception {
        Task sourceTask = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationExecutionDto sourceExecution = lifecycleService.start(
                sourceTask.getId(), startRequest(sourceTask), copilotActor);
        byte[] content = "reference evidence".getBytes(StandardCharsets.UTF_8);
        IntegrationArtifactDto sourceDto = artifactService.upload(
                sourceExecution.executionId(),
                artifactMetadata("reference.txt", content),
                content,
                copilotActor);
        lifecycleService.submit(
                sourceExecution.executionId(),
                new SubmitExecutionRequest("complete", List.of(sourceDto.artifactId())),
                copilotActor);

        Task targetTask = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationExecutionDto targetExecution = lifecycleService.start(
                targetTask.getId(), startRequest(targetTask), copilotActor);
        var source = artifactRepository.findWithProvenance(sourceDto.artifactId()).orElseThrow();
        source.setContentExpiresAt(Instant.now().plusSeconds(1));
        artifactRepository.saveAndFlush(source);

        artifactService.reference(
                targetExecution.executionId(),
                new com.wwa.agenthub.contracts.dto.integration.ExternalArtifactRequest(
                        artifactMetadata("reference.txt", content), sourceDto.artifactId()),
                copilotActor);

        var renewed = artifactRepository.findWithProvenance(sourceDto.artifactId()).orElseThrow();
        assertThat(renewed.isLegalHold()).isFalse();
        assertThat(renewed.getContentExpiresAt()).isAfter(Instant.now().plusSeconds(60));
    }

    @Test
    void archivedIntegrationEvidenceIsRetentionProtectedFromPurge() {
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationActor admin = actor("admin", "atlas-web", IntegrationClientType.MANUAL, false,
                "DEVOPS_ADMIN");
        releaseFlowService.archiveRequestRundown(
                request.getReleaseFlow().getId(), request.getId(), admin.user());

        assertThatThrownBy(() -> releaseFlowService.purgeArchivedRequestRundown(
                request.getReleaseFlow().getId(), request.getId(), admin.user()))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("retention-protected");
        assertThat(taskRepository.findById(task.getId())).isPresent();
    }

    private static ArtifactUploadMetadata artifactMetadata(String name, byte[] content)
            throws Exception {
        return new ArtifactUploadMetadata(
                ArtifactRole.EVIDENCE,
                ArtifactKind.TEXT,
                name,
                "text/plain",
                content.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                "reports/" + name);
    }

    private IntegrationActor actor(
            String userId,
            String applicationId,
            IntegrationClientType clientType,
            boolean bearer,
            String... roles
    ) {
        List<String> roleList = roles.length == 0 ? List.of("DEVELOPER") : List.of(roles);
        UserContext user = new UserContext(
                userId,
                roleList.getFirst(),
                roleList,
                Set.of(),
                userId,
                List.of(new AccessScope("*", "*")));
        return new IntegrationActor(
                user,
                applicationId,
                clientType,
                "1.0",
                Set.of(request == null ? "deployment-agent" : request.getAgent()),
                bearer);
    }

    private IntegrationActor scopedActor(
            String userId,
            String application,
            String snowGroup,
            boolean bearer
    ) {
        UserContext user = new UserContext(
                userId,
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(),
                userId,
                List.of(new AccessScope(application, snowGroup)));
        return new IntegrationActor(
                user,
                "scoped-client",
                IntegrationClientType.MANUAL,
                "1.0",
                Set.of(request.getAgent()),
                bearer);
    }

    private static StartExecutionRequest startRequest(Task task) {
        return new StartExecutionRequest(
                "1.0",
                new StartExecutionRequest.Capability(
                        task.getCapabilityId(),
                        task.getCapabilityType(),
                        task.getCapabilityVersion()),
                new StartExecutionRequest.ProjectContext(
                        task.getRequest().getReleaseFlow().getProjectId(),
                        task.getRepositoryId(),
                        task.getRepositoryBranch(),
                        task.getRepositoryCommit()));
    }
}
