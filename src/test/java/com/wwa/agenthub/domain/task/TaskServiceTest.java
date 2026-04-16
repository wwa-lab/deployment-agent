package com.wwa.agenthub.domain.task;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.ExecutionType;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.errors.ConflictAppException;
import com.wwa.agenthub.errors.ForbiddenAppException;
import com.wwa.agenthub.errors.InvalidStateTransitionException;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.errors.ValidationAppException;
import com.wwa.agenthub.helper.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("TaskService")
class TaskServiceTest {

    @Autowired private TaskService taskService;
    @Autowired private TestDataHelper helper;

    private ReleaseFlow releaseFlow;
    private Request request;
    private UserContext ownerUser;
    private UserContext adminUser;
    private UserContext nonOwnerUser;

    @BeforeEach
    void setUp() {
        releaseFlow = helper.seedReleaseFlow();
        request = helper.seedRequest(releaseFlow);
        ownerUser = new UserContext("emp-001", "DEVELOPER");
        adminUser = new UserContext("emp-003", "DEVOPS_ADMIN");
        nonOwnerUser = new UserContext("dev-user", "DEVELOPER");
    }

    // ─── create ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("creates task in Pending status with all required fields")
    void create_setsAllFieldsAndPendingStatus() {
        Map<String, Object> input = Map.of("script", "deploy.sh", "parameters", "--env staging");
        CreateTaskInput createInput = new CreateTaskInput(
                request, "TG-001", "Deploy App", 1, "deploy-app",
                ExecutionType.AUTO, true, input, "Deployment successful", "alice",
                null, null, null);

        Task task = taskService.create(createInput);

        assertThat(task.getId()).isNotNull();
        assertThat(task.getTaskGroupId()).isEqualTo("TG-001");
        assertThat(task.getTaskGroupName()).isEqualTo("Deploy App");
        assertThat(task.getStepSeq()).isEqualTo(1);
        assertThat(task.getTaskName()).isEqualTo("deploy-app");
        assertThat(task.getExecutionType()).isEqualTo(ExecutionType.AUTO);
        assertThat(task.isCritical()).isTrue();
        assertThat(task.getTaskStatus()).isEqualTo(TaskStatus.Pending);
        assertThat(task.getInputParameters()).containsEntry("script", "deploy.sh");
        assertThat(task.getExpectedOutput()).isEqualTo("Deployment successful");
        assertThat(task.getOwner()).isEqualTo("alice");
        assertThat(task.getCurrentResultSummary()).isNull();
        assertThat(task.getLatestExecutionId()).isNull();
    }

    @Test
    @DisplayName("creates a MANUAL task")
    void create_manualTask() {
        CreateTaskInput createInput = new CreateTaskInput(
                request, "TG-002", "Manual Step", 1, "manual-step",
                ExecutionType.MANUAL, false, null, null, null, null, null, null);

        Task task = taskService.create(createInput);

        assertThat(task.getExecutionType()).isEqualTo(ExecutionType.MANUAL);
        assertThat(task.isCritical()).isFalse();
        assertThat(task.getTaskStatus()).isEqualTo(TaskStatus.Pending);
    }

    // ─── updateStatus ────────────────────────────────────────────────────────

    @Test
    @DisplayName("allows valid transition Pending → Ready_For_Execution")
    void updateStatus_validTransition() {
        Task task = helper.seedTask(request, TaskStatus.Pending);

        Task updated = taskService.updateStatus(task.getId(), TaskStatus.Ready_For_Execution, ownerUser, null);

        assertThat(updated.getTaskStatus()).isEqualTo(TaskStatus.Ready_For_Execution);
    }

    @Test
    @DisplayName("throws InvalidStateTransitionException for disallowed transition")
    void updateStatus_invalidTransition_throws() {
        Task task = helper.seedTask(request, TaskStatus.Pending);

        assertThatThrownBy(() ->
                taskService.updateStatus(task.getId(), TaskStatus.Approved, ownerUser, null))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("throws NotFoundAppException for unknown task ID")
    void updateStatus_unknownTask_throws() {
        assertThatThrownBy(() ->
                taskService.updateStatus("non-existent-id", TaskStatus.Approved, ownerUser, null))
                .isInstanceOf(NotFoundAppException.class);
    }

    // ─── editInput ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("edits input when task is in Pending state")
    void editInput_pendingState_succeeds() {
        Task task = helper.seedTask(request, TaskStatus.Pending);
        Map<String, Object> newInput = Map.of("script", "new_deploy.sh");

        Task updated = taskService.editInput(task.getId(), newInput, ownerUser);

        assertThat(updated.getInputParameters()).containsEntry("script", "new_deploy.sh");
    }

    @Test
    @DisplayName("allows admin to edit input when task is in Ready_For_Execution state")
    void editInput_adminReadyState_succeeds() {
        Task task = helper.seedTask(request, TaskStatus.Ready_For_Execution);
        Map<String, Object> newInput = Map.of("script", "v2_deploy.sh");

        Task updated = taskService.editInput(task.getId(), newInput, adminUser);

        assertThat(updated.getInputParameters()).containsEntry("script", "v2_deploy.sh");
    }

    @Test
    @DisplayName("partial input edits preserve unspecified task input fields")
    void editInput_partialUpdate_preservesExistingFields() {
        Task task = taskService.create(new CreateTaskInput(
                request,
                "TG-ANSIBLE",
                "Ansible Group",
                1,
                "ansible-step",
                ExecutionType.AUTO,
                true,
                Map.of("script", "42", "parameters", "{\"inventory\":\"sit\"}", "system", "ANSIBLE"),
                null,
                "alice",
                null,
                null,
                null));

        Task updated = taskService.editInput(task.getId(), Map.of("script", "84"), ownerUser);

        assertThat(updated.getInputParameters())
                .containsEntry("script", "84")
                .containsEntry("parameters", "{\"inventory\":\"sit\"}")
                .containsEntry("system", "ANSIBLE");
    }

    @Test
    @DisplayName("throws ForbiddenAppException when non-owner developer edits input")
    void editInput_nonOwner_throwsForbidden() {
        Task task = helper.seedTask(request, TaskStatus.Pending);

        assertThatThrownBy(() ->
                taskService.editInput(task.getId(), Map.of("script", "x"), nonOwnerUser))
                .isInstanceOf(ForbiddenAppException.class);
    }

    @Test
    @DisplayName("throws ValidationAppException when editing in Executing state")
    void editInput_executingState_throws() {
        Task task = helper.seedTask(request, TaskStatus.Executing);

        assertThatThrownBy(() ->
                taskService.editInput(task.getId(), Map.of("script", "x"), ownerUser))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("Executing");
    }

    @Test
    @DisplayName("throws ValidationAppException when input is null")
    void editInput_nullInput_throws() {
        Task task = helper.seedTask(request, TaskStatus.Pending);

        assertThatThrownBy(() ->
                taskService.editInput(task.getId(), null, ownerUser))
                .isInstanceOf(ValidationAppException.class);
    }

    @Test
    @DisplayName("editCustomFields merges task doc overrides without dropping existing custom fields")
    void editCustomFields_mergesOverrides() {
        Task task = helper.seedTask(request, TaskStatus.Pending);
        task.setCustomFields(Map.of("source", "template"));

        Task updated = taskService.editCustomFields(task.getId(), Map.of(
                "taskDocs", Map.of(
                        "inputs", java.util.List.of(Map.of(
                                "label", "Requirement Package",
                                "url", "https://github.com/example/requirement.md",
                                "required", true)),
                        "outputs", java.util.List.of())), ownerUser);

        assertThat(updated.getCustomFields())
                .containsEntry("source", "template");
        assertThat(updated.getCustomFields())
                .containsKey("taskDocs");
    }

    // ─── startManualExecution ────────────────────────────────────────────────

    @Test
    @DisplayName("startManualExecution transitions MANUAL task Ready_For_Execution → Executing")
    void startManualExecution_manualReady_transitionsToExecuting() {
        Task task = createManualTask(TaskStatus.Ready_For_Execution);

        Task started = taskService.startManualExecution(task.getId(), ownerUser);

        assertThat(started.getTaskStatus()).isEqualTo(TaskStatus.Executing);
    }

    @Test
    @DisplayName("startManualExecution rejects AUTO tasks")
    void startManualExecution_autoTask_throwsConflict() {
        Task task = helper.seedTask(request, TaskStatus.Ready_For_Execution);

        assertThatThrownBy(() -> taskService.startManualExecution(task.getId(), ownerUser))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("not a MANUAL task");
    }

    @Test
    @DisplayName("startManualExecution rejects non-ready MANUAL states")
    void startManualExecution_manualPending_throwsConflict() {
        Task task = createManualTask(TaskStatus.Pending);

        assertThatThrownBy(() -> taskService.startManualExecution(task.getId(), ownerUser))
                .isInstanceOf(ConflictAppException.class)
                .hasMessageContaining("Ready_For_Execution");
    }

    // ─── updateResultMetadata ────────────────────────────────────────────────

    @Test
    @DisplayName("updates result metadata with summary and execution id")
    void updateResultMetadata_setsFields() {
        Task task = helper.seedTask(request);
        Map<String, Object> summary = Map.of("status", "ok", "output", "done");

        Task updated = taskService.updateResultMetadata(task.getId(), summary, "exec-001");

        assertThat(updated.getCurrentResultSummary()).containsEntry("status", "ok");
        assertThat(updated.getLatestExecutionId()).isEqualTo("exec-001");
    }

    private Task createManualTask(TaskStatus status) {
        Task created = taskService.create(new CreateTaskInput(
                request,
                "TG-MANUAL",
                "Manual Group",
                1,
                "manual-step",
                ExecutionType.MANUAL,
                false,
                Map.of("script", "deploy.sh", "parameters", "--env sit"),
                null,
                "alice",
                null,
                null,
                null));
        if (status == TaskStatus.Pending) {
            return created;
        }
        if (status == TaskStatus.Ready_For_Execution) {
            return taskService.updateStatus(created.getId(), TaskStatus.Ready_For_Execution, ownerUser, null);
        }
        throw new IllegalArgumentException("Unsupported status for manual task helper: " + status);
    }
}
