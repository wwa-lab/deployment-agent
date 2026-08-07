package com.wwa.agenthub.platform.integration;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.integration.ArtifactUploadMetadata;
import com.wwa.agenthub.contracts.dto.integration.ExternalArtifactRequest;
import com.wwa.agenthub.contracts.dto.integration.IntegrationArtifactDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationExecutionDto;
import com.wwa.agenthub.contracts.dto.integration.StartExecutionRequest;
import com.wwa.agenthub.contracts.dto.integration.SubmitExecutionRequest;
import com.wwa.agenthub.contracts.enums.ArtifactKind;
import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowService;
import com.wwa.agenthub.domain.releaseflow.RequestRepository;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryRepository;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.helper.TestDataHelper;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.artifact.ArtifactRetentionService;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactRepository;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactService;
import com.wwa.agenthub.platform.domain.integration.lifecycle.ExecutionLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:atlas-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=Oracle"
})
@ActiveProfiles("test")
class AtlasExecutionConcurrencyIntegrationTest {

    @Autowired private TestDataHelper helper;
    @Autowired private ExecutionLifecycleService lifecycleService;
    @Autowired private TaskExecutionHistoryRepository executionRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ReleaseFlowService releaseFlowService;
    @Autowired private RequestRepository requestRepository;
    @Autowired private IntegrationArtifactService artifactService;
    @Autowired private IntegrationArtifactRepository artifactRepository;
    @Autowired private ArtifactRetentionService artifactRetentionService;

    @Test
    void simultaneousStartsProduceExactlyOneActiveAttempt() throws Exception {
        ReleaseFlow flow = helper.seedReleaseFlow("atlas-start");
        Request request = helper.seedRequest(flow);
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationActor actor = new IntegrationActor(
                new UserContext(
                        "alice",
                        "DEVELOPER",
                        List.of("DEVELOPER"),
                        Set.of(),
                        "Alice",
                        List.of(new AccessScope("*", "*"))),
                "manual-client",
                IntegrationClientType.MANUAL,
                "1.0",
                Set.of(request.getAgent()),
                true);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            StartExecutionRequest command = startRequest(task);
            var first = executor.submit(() -> runStart(start, task.getId(), command, actor));
            var second = executor.submit(() -> runStart(start, task.getId(), command, actor));
            start.countDown();

            List<Boolean> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        }

        assertThat(executionRepository.findByTaskIdOrderByAttemptNumberAsc(task.getId()))
                .singleElement()
                .satisfies(execution -> {
                    assertThat(execution.getAttemptNumber()).isEqualTo(1);
                    assertThat(execution.getExecutionStatus().name()).isEqualTo("Running");
                });
        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(persisted.getActiveExecutionId()).isNotBlank();
        assertThat(persisted.getLatestExecutionId()).isEqualTo(persisted.getActiveExecutionId());
    }

    @Test
    void archiveAndStartCannotProduceRunningExecutionOnArchivedRequest() throws Exception {
        ReleaseFlow flow = helper.seedReleaseFlow("atlas-archive");
        Request request = helper.seedRequest(flow);
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationActor actor = actor(request, "alice", "manual-client", true, "DEVELOPER");
        IntegrationActor admin = actor(request, "admin", "atlas-web", false, "DEVOPS_ADMIN");

        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var execution = executor.submit(() -> runStart(
                    start, task.getId(), startRequest(task), actor));
            var archive = executor.submit(() -> runArchive(
                    start, flow.getId(), request.getId(), admin.user()));
            start.countDown();
            outcomes = List.of(
                    execution.get(10, TimeUnit.SECONDS),
                    archive.get(10, TimeUnit.SECONDS));
        }

        assertThat(outcomes).contains(true);
        Task persistedTask = taskRepository.findById(task.getId()).orElseThrow();
        Request persistedRequest = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(persistedRequest.getArchivedAt() != null
                && persistedTask.getActiveExecutionId() != null).isFalse();
    }

    @Test
    void mutuallyReferencedArtifactsSubmitWithoutLockOrderDeadlock() throws Exception {
        ReleaseFlow flow = helper.seedReleaseFlow("atlas-mutual-artifacts");
        Request request = helper.seedRequest(flow);
        Task firstTask = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        request = requestRepository.findById(request.getId()).orElseThrow();
        Task secondTask = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        firstTask = taskRepository.findIntegrationTaskById(firstTask.getId()).orElseThrow();
        secondTask = taskRepository.findIntegrationTaskById(secondTask.getId()).orElseThrow();
        IntegrationActor actor = actor(request, "alice", "manual-client", true, "DEVELOPER");
        IntegrationExecutionDto firstExecution = lifecycleService.start(
                firstTask.getId(), startRequest(firstTask), actor);
        IntegrationExecutionDto secondExecution = lifecycleService.start(
                secondTask.getId(), startRequest(secondTask), actor);

        byte[] firstContent = "first execution evidence".getBytes(StandardCharsets.UTF_8);
        byte[] secondContent = "second execution evidence".getBytes(StandardCharsets.UTF_8);
        ArtifactUploadMetadata firstMetadata = artifactMetadata("first.txt", firstContent);
        ArtifactUploadMetadata secondMetadata = artifactMetadata("second.txt", secondContent);
        IntegrationArtifactDto firstUpload = artifactService.upload(
                firstExecution.executionId(), firstMetadata, firstContent, actor);
        IntegrationArtifactDto secondUpload = artifactService.upload(
                secondExecution.executionId(), secondMetadata, secondContent, actor);
        IntegrationArtifactDto firstReference = artifactService.reference(
                firstExecution.executionId(),
                new ExternalArtifactRequest(secondMetadata, secondUpload.artifactId()),
                actor);
        IntegrationArtifactDto secondReference = artifactService.reference(
                secondExecution.executionId(),
                new ExternalArtifactRequest(firstMetadata, firstUpload.artifactId()),
                actor);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> runSubmit(
                    start,
                    firstExecution.executionId(),
                    List.of(firstUpload.artifactId(), firstReference.artifactId()),
                    actor));
            var second = executor.submit(() -> runSubmit(
                    start,
                    secondExecution.executionId(),
                    List.of(secondUpload.artifactId(), secondReference.artifactId()),
                    actor));
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void retentionCleanupAndSubmitSerializeOnTheSameArtifactLockOrder() throws Exception {
        ReleaseFlow flow = helper.seedReleaseFlow("atlas-retention-submit");
        Request request = helper.seedRequest(flow);
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationActor actor = actor(request, "alice", "manual-client", true, "DEVELOPER");
        IntegrationExecutionDto execution = lifecycleService.start(
                task.getId(), startRequest(task), actor);
        byte[] content = "expiring execution evidence".getBytes(StandardCharsets.UTF_8);
        IntegrationArtifactDto upload = artifactService.upload(
                execution.executionId(), artifactMetadata("expiring.txt", content), content, actor);
        var artifact = artifactRepository.findById(upload.artifactId()).orElseThrow();
        artifact.setContentExpiresAt(Instant.now().minusSeconds(1));
        artifactRepository.saveAndFlush(artifact);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var submit = executor.submit(() -> runSubmit(
                    start, execution.executionId(), List.of(upload.artifactId()), actor));
            var cleanup = executor.submit(() -> runCleanup(start));
            start.countDown();

            boolean submitted = submit.get(10, TimeUnit.SECONDS);
            int purged = cleanup.get(10, TimeUnit.SECONDS);
            assertThat((submitted && purged == 0) || (!submitted && purged == 1)).isTrue();
        }
    }

    private boolean runStart(
            CountDownLatch latch,
            String taskId,
            StartExecutionRequest command,
            IntegrationActor actor
    ) {
        try {
            latch.await();
            lifecycleService.start(taskId, command, actor);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean runArchive(
            CountDownLatch latch,
            String flowId,
            String requestId,
            UserContext user
    ) {
        try {
            latch.await();
            releaseFlowService.archiveRequestRundown(flowId, requestId, user);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean runSubmit(
            CountDownLatch latch,
            String executionId,
            List<String> artifactIds,
            IntegrationActor actor
    ) {
        try {
            latch.await();
            lifecycleService.submit(
                    executionId, new SubmitExecutionRequest("client summary", artifactIds), actor);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private int runCleanup(CountDownLatch latch) {
        try {
            latch.await();
            return artifactRetentionService.purgeExpiredContent();
        } catch (Exception exception) {
            return -1;
        }
    }

    private static IntegrationActor actor(
            Request request,
            String userId,
            String clientId,
            boolean bearer,
            String role
    ) {
        return new IntegrationActor(
                new UserContext(
                        userId,
                        role,
                        List.of(role),
                        Set.of(),
                        userId,
                        List.of(new AccessScope("*", "*"))),
                clientId,
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

    private static ArtifactUploadMetadata artifactMetadata(String name, byte[] content)
            throws Exception {
        return new ArtifactUploadMetadata(
                ArtifactRole.EVIDENCE,
                ArtifactKind.REPORT,
                name,
                "text/plain",
                content.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                "reports/" + name);
    }
}
