package com.wwa.agenthub.platform.integration;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.integration.ArtifactUploadMetadata;
import com.wwa.agenthub.contracts.dto.integration.IntegrationArtifactDto;
import com.wwa.agenthub.contracts.dto.integration.IntegrationExecutionDto;
import com.wwa.agenthub.contracts.dto.integration.StartExecutionRequest;
import com.wwa.agenthub.contracts.enums.ArtifactKind;
import com.wwa.agenthub.contracts.enums.ArtifactRole;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.helper.TestDataHelper;
import com.wwa.agenthub.platform.domain.integration.artifact.ArtifactRetentionService;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactRepository;
import com.wwa.agenthub.platform.domain.integration.artifact.IntegrationArtifactService;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import com.wwa.agenthub.platform.domain.integration.lifecycle.ExecutionLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:artifact-retention;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=Oracle",
        "app.integration.artifact-retention-cleanup-batch-size=2",
        "app.integration.artifact-retention-cleanup-max-batches-per-run=2"
})
@ActiveProfiles("test")
class ArtifactRetentionServiceIntegrationTest {

    @Autowired private TestDataHelper helper;
    @Autowired private ExecutionLifecycleService lifecycleService;
    @Autowired private IntegrationArtifactService artifactService;
    @Autowired private IntegrationArtifactRepository artifactRepository;
    @Autowired private ArtifactRetentionService retentionService;

    @Test
    void scheduledRunDrainsBoundedIndependentBatchesAndRetainsMetadata() throws Exception {
        ReleaseFlow flow = helper.seedReleaseFlow("retention-batches");
        Request request = helper.seedRequest(flow);
        Task task = helper.seedIntegrationTask(
                request, TaskStatus.Ready_For_Execution, CapabilityType.MANUAL, "alice");
        IntegrationActor actor = actor(request);
        IntegrationExecutionDto execution = lifecycleService.start(
                task.getId(), startRequest(task), actor);
        List<IntegrationArtifactDto> uploaded = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            byte[] content = ("expired report " + index).getBytes(StandardCharsets.UTF_8);
            IntegrationArtifactDto artifact = artifactService.upload(
                    execution.executionId(),
                    metadata("expired-" + index + ".txt", content),
                    content,
                    actor);
            var persisted = artifactRepository.findWithProvenance(artifact.artifactId()).orElseThrow();
            persisted.setContentExpiresAt(Instant.now().minusSeconds(1));
            artifactRepository.saveAndFlush(persisted);
            uploaded.add(artifact);
        }

        assertThat(retentionService.purgeExpiredContent()).isEqualTo(4);
        assertThat(uploaded)
                .map(IntegrationArtifactDto::artifactId)
                .map(id -> artifactRepository.findWithProvenance(id).orElseThrow())
                .filteredOn(artifact -> artifact.getContent() != null)
                .hasSize(1);

        assertThat(retentionService.purgeExpiredContent()).isEqualTo(1);
        assertThat(uploaded)
                .map(IntegrationArtifactDto::artifactId)
                .map(id -> artifactRepository.findWithProvenance(id).orElseThrow())
                .allSatisfy(artifact -> {
                    assertThat(artifact.getContent()).isNull();
                    assertThat(artifact.getContentPurgedAt()).isNotNull();
                    assertThat(artifact.getSha256()).hasSize(64);
                });
    }

    private static IntegrationActor actor(Request request) {
        return new IntegrationActor(
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

    private static ArtifactUploadMetadata metadata(String name, byte[] content) throws Exception {
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
