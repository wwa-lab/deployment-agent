package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.errors.InvalidStateTransitionException;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import com.wwa.deploymentagent.errors.OptimisticLockConflictException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * TaskService – Task CRUD and lifecycle management.
 *
 * <p>Enforces state machine transitions and audits all state changes.
 * Handles optimistic locking via JPA {@code @Version}.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final AuditLoggerService auditLogger;
    private final TaskPermissionService taskPermissionService;

    /** Retrieve a task by ID. Throws {@link NotFoundAppException} if not found. */
    @Transactional(readOnly = true)
    public Task getById(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));
    }

    /** List all tasks for a given request ID, ordered by (taskGroupId, stepSeq). */
    @Transactional(readOnly = true)
    public List<Task> listByRequestId(String requestId) {
        return taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(requestId);
    }

    /**
     * Create a new task in Pending status.
     * All required template-derived fields must be provided.
     */
    @Transactional
    public Task create(CreateTaskInput input) {
        Task task = new Task();
        task.setRequest(input.request());
        task.setTaskGroupId(input.taskGroupId());
        task.setTaskGroupName(input.taskGroupName());
        task.setStepSeq(input.stepSeq());
        task.setTaskName(input.taskName());
        task.setExecutionType(input.executionType());
        task.setCritical(input.critical());
        task.setTaskStatus(TaskStatus.Pending);
        task.setInputParameters(input.inputParameters());
        task.setExpectedOutput(input.expectedOutput());
        task.setOwner(input.owner());
        task.setPlannedStartTime(input.plannedStartTime());
        task.setPlannedEndTime(input.plannedEndTime());
        task.setImportMetadata(input.importMetadata());
        task.setCurrentResultSummary(null);
        task.setLatestExecutionId(null);
        task.setStartTime(null);
        task.setEndTime(null);

        return save(task);
    }

    /**
     * Update task status with transition validation.
     * Throws {@link InvalidStateTransitionException} if transition is disallowed.
     * Audits the transition.
     */
    @Transactional
    public Task updateStatus(String taskId, TaskStatus newStatus, UserContext user, String comment) {
        Task task = getById(taskId);
        assertTaskRequestActive(task);

        if (!TaskStateMachine.isValid(task.getTaskStatus(), newStatus)) {
            throw new InvalidStateTransitionException(
                    task.getTaskStatus().name(), newStatus.name(), "Task");
        }

        TaskStatus previous = task.getTaskStatus();
        task.setTaskStatus(newStatus);
        Task saved = save(task);

        auditLogger.log(user, AuditActionType.edit,
                task.getRequest().getReleaseFlow().getId(),
                task.getRequest().getId(),
                taskId,
                Map.of("transitionFrom", previous.name(),
                       "transitionTo", newStatus.name(),
                       "comment", comment != null ? comment : ""));

        return saved;
    }

    /**
     * Edit task input parameters.
     * Only allowed in Pending or Ready_For_Execution states.
     * Validates that the input is a non-null map and audits the change.
     */
    @Transactional
    public Task editInput(String taskId, Map<String, Object> newInput, UserContext user) {
        Task task = getById(taskId);
        assertTaskRequestActive(task);
        taskPermissionService.assertOwnerOrAdmin(task, user, "task:editInput");

        if (task.getTaskStatus() != TaskStatus.Pending
                && task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            throw new ValidationAppException(
                    "Task input can only be edited in Pending or Ready_For_Execution states. "
                    + "Current state: " + task.getTaskStatus().name());
        }
        if (newInput == null) {
            throw new ValidationAppException("Input parameters must not be null");
        }

        Map<String, Object> oldInput = task.getInputParameters();
        task.setInputParameters(newInput);
        Task saved = save(task);

        auditLogger.log(user, AuditActionType.edit,
                task.getRequest().getReleaseFlow().getId(),
                task.getRequest().getId(),
                taskId,
                Map.of("fieldChanged", "inputParameters",
                       "oldValue", oldInput != null ? oldInput : Map.of(),
                       "newValue", newInput));

        return saved;
    }

    /**
     * Update the result metadata for a task.
     * Sets currentResultSummary and latestExecutionId.
     */
    @Transactional
    public Task updateResultMetadata(String taskId,
                                     Map<String, Object> resultSummary,
                                     String executionId) {
        Task task = getById(taskId);
        assertTaskRequestActive(task);
        task.setCurrentResultSummary(resultSummary);
        task.setLatestExecutionId(executionId);
        return save(task);
    }

    public void assertTaskRequestActive(Task task) {
        if (task.getRequest().getArchivedAt() != null
                || task.getRequest().getReleaseFlow().getArchivedAt() != null) {
            throw new ValidationAppException("Archived rundowns are read-only until restored.");
        }
    }

    private Task save(Task task) {
        try {
            return taskRepository.save(task);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockConflictException("Task");
        }
    }
}
