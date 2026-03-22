package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.ExecutionStatus;
import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistory;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistoryService;
import com.wwa.deploymentagent.domain.task.TaskService;
import com.wwa.deploymentagent.helper.TestDataHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T13.4 - Result persistence (CLOB roundtrip) and pagination correctness tests.
 *
 * Verifies that large JSON payloads survive the CLOB storage roundtrip,
 * that nested JSON structures are preserved, and that pagination behaves correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ResultPersistence")
class ResultPersistenceTest {

    @Autowired private TaskExecutionHistoryService executionHistoryService;
    @Autowired private TaskService taskService;
    @Autowired private ReleaseFlowService releaseFlowService;
    @Autowired private ReleaseFlowRepository releaseFlowRepository;
    @Autowired private TestDataHelper helper;

    private final UserContext ownerUser = new UserContext("emp-001", "DEVELOPER");

    // ─── CLOB storage roundtrip ───────────────────────────────────────────────

    @Test
    @DisplayName("TaskExecutionHistory resultSummary CLOB roundtrip preserves all 100+ keys")
    void taskExecutionHistory_clobStorageRoundtrip() {
        // Seed hierarchy
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req, TaskStatus.Ready_For_Execution);

        // Build a large result summary map with 100+ keys
        Map<String, Object> largeResultSummary = new HashMap<>();
        for (int i = 0; i < 120; i++) {
            largeResultSummary.put("key_" + i, "value_" + i + "_data");
        }
        largeResultSummary.put("status", "completed");
        largeResultSummary.put("exitCode", "0");

        // Create execution and then complete it with the large summary
        TaskExecutionHistory exec = executionHistoryService.createExecution(task.getId());
        executionHistoryService.completeExecution(
                exec.getId(),
                ExecutionStatus.Completed,
                largeResultSummary,
                "Full execution log output");

        // Retrieve via findLatest and verify CLOB roundtrip
        Optional<TaskExecutionHistory> latest = executionHistoryService.findLatest(task.getId());

        assertThat(latest).isPresent();
        Map<String, Object> retrieved = latest.get().getResultSummary();
        assertThat(retrieved).isNotNull();

        // Verify all 120 generated keys are present
        for (int i = 0; i < 120; i++) {
            assertThat(retrieved).containsKey("key_" + i);
            assertThat(retrieved.get("key_" + i)).isEqualTo("value_" + i + "_data");
        }
        assertThat(retrieved).containsEntry("status", "completed");
        assertThat(retrieved).containsEntry("exitCode", "0");
    }

    // ─── Pagination ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("list pagination returns correct page slices with accurate totalElements")
    void releaseFlowList_pagination_returnsCorrectPage() {
        // Seed 3 release flows with distinct project IDs to avoid unique-index collisions
        seedDistinctReleaseFlow("PAGINATION-PROJ-A", "sit-pagination-a-0001");
        seedDistinctReleaseFlow("PAGINATION-PROJ-B", "sit-pagination-b-0001");
        seedDistinctReleaseFlow("PAGINATION-PROJ-C", "sit-pagination-c-0001");

        // Page 0, size 2 → expect 2 items
        Page<ReleaseFlow> page0 = releaseFlowService.list(null, null, null, PageRequest.of(0, 2));
        assertThat(page0.getContent()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(page0.getSize()).isEqualTo(2);

        // Page 1, size 2 → expect at least 1 item (the third seeded flow)
        Page<ReleaseFlow> page1 = releaseFlowService.list(null, null, null, PageRequest.of(1, 2));
        assertThat(page1.getContent()).isNotEmpty();

        // Total elements must include at least the 3 we seeded
        assertThat(page0.getTotalElements()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("list filtered by projectId pages only matching release flows")
    void releaseFlowList_pagination_filteredByProject_returnsOnlyMatches() {
        String uniqueProjectId = "PAGINATION-UNIQUE-" + UUID.randomUUID().toString().substring(0, 8);
        seedDistinctReleaseFlow(uniqueProjectId,
                "sit-" + uniqueProjectId.toLowerCase().replaceAll("[^a-z0-9]", "") + "-0001");

        Page<ReleaseFlow> result = releaseFlowService.list(
                uniqueProjectId, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getProjectId()).isEqualTo(uniqueProjectId);
    }

    // ─── JSON roundtrip ───────────────────────────────────────────────────────

    @Test
    @DisplayName("task inputParameters JSON roundtrip preserves nested structure")
    void taskInputParameters_jsonRoundtrip() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req, TaskStatus.Pending);

        // Edit with a nested JSON structure
        Map<String, Object> nestedEnv = new HashMap<>();
        nestedEnv.put("JAVA_HOME", "/usr/lib/jvm/java-21");
        nestedEnv.put("MAVEN_OPTS", "-Xmx2g -Xms512m");
        nestedEnv.put("DEBUG", Boolean.FALSE);

        Map<String, Object> complexInput = new HashMap<>();
        complexInput.put("script", "deploy-app.sh");
        complexInput.put("parameters", "--env staging --version 2.1.0");
        complexInput.put("env", nestedEnv);
        complexInput.put("timeout", 300);

        taskService.editInput(task.getId(), complexInput, ownerUser);

        // Retrieve and verify nested JSON preserved
        Task retrieved = taskService.getById(task.getId());
        Map<String, Object> storedInput = retrieved.getInputParameters();

        assertThat(storedInput).isNotNull();
        assertThat(storedInput).containsEntry("script", "deploy-app.sh");
        assertThat(storedInput).containsEntry("parameters", "--env staging --version 2.1.0");
        assertThat(storedInput).containsKey("env");
        assertThat(storedInput).containsEntry("timeout", 300);

        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedEnv = (Map<String, Object>) storedInput.get("env");
        assertThat(retrievedEnv).containsEntry("JAVA_HOME", "/usr/lib/jvm/java-21");
        assertThat(retrievedEnv).containsEntry("MAVEN_OPTS", "-Xmx2g -Xms512m");
        assertThat(retrievedEnv).containsEntry("DEBUG", Boolean.FALSE);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Seed a Release Flow directly via repository to allow distinct project IDs.
     * TestDataHelper.seedReleaseFlow() always uses "PROJ-001", which would collide
     * on the unique (project_id, normalized_release_id) index if called multiple times.
     */
    private ReleaseFlow seedDistinctReleaseFlow(String projectId, String normalizedReleaseId) {
        ReleaseFlow rf = new ReleaseFlow();
        rf.setProjectId(projectId);
        rf.setProjectName("Project " + projectId);
        rf.setReleaseId(normalizedReleaseId);
        rf.setNormalizedReleaseId(normalizedReleaseId);
        rf.setCurrentStage(Stage.SIT);
        rf.setFlowStatus(FlowStatus.Pending);
        rf.setReviewStatus(ReviewStatus.Pending_Review);
        return releaseFlowRepository.save(rf);
    }
}
