package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.errors.ConflictAppException;
import com.wwa.deploymentagent.errors.InvalidStateTransitionException;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import com.wwa.deploymentagent.errors.OptimisticLockConflictException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
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
        Map<String, Object> mergedInput = new LinkedHashMap<>();
        if (oldInput != null) {
            mergedInput.putAll(oldInput);
        }
        mergedInput.putAll(newInput);

        task.setInputParameters(mergedInput);
        Task saved = save(task);

        auditLogger.log(user, AuditActionType.edit,
                task.getRequest().getReleaseFlow().getId(),
                task.getRequest().getId(),
                taskId,
                Map.of("fieldChanged", "inputParameters",
                       "oldValue", oldInput != null ? oldInput : Map.of(),
                       "newValue", mergedInput));

        return saved;
    }

    /**
     * Edit per-agent custom fields.
     * Unlike execution input parameters, these fields are presentation metadata
     * and may be updated whenever the owning request is still active.
     */
    @Transactional
    public Task editCustomFields(String taskId, Map<String, Object> newCustomFields, UserContext user) {
        Task task = getById(taskId);
        assertTaskRequestActive(task);
        taskPermissionService.assertOwnerOrAdmin(task, user, "task:editCustomFields");

        if (newCustomFields == null) {
            throw new ValidationAppException("Custom fields must not be null");
        }

        Map<String, Object> oldCustomFields = task.getCustomFields();
        Map<String, Object> mergedCustomFields = new LinkedHashMap<>();
        if (oldCustomFields != null) {
            mergedCustomFields.putAll(oldCustomFields);
        }
        mergedCustomFields.putAll(newCustomFields);

        task.setCustomFields(mergedCustomFields);
        Task saved = save(task);

        auditLogger.log(user, AuditActionType.edit,
                task.getRequest().getReleaseFlow().getId(),
                task.getRequest().getId(),
                taskId,
                Map.of("fieldChanged", "customFields",
                       "oldValue", oldCustomFields != null ? oldCustomFields : Map.of(),
                       "newValue", mergedCustomFields));

        return saved;
    }

    /**
     * Edit task name and/or step (group) name.
     * Only allowed in Pending or Ready_For_Execution states.
     */
    @Transactional
    public Task editNames(String taskId, String newTaskName, String newTaskGroupName, UserContext user) {
        Task task = getById(taskId);
        assertTaskRequestActive(task);
        taskPermissionService.assertOwnerOrAdmin(task, user, "task:editNames");

        if (task.getTaskStatus() != TaskStatus.Pending
                && task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            throw new ValidationAppException(
                    "Task names can only be edited in Pending or Ready_For_Execution states. "
                    + "Current state: " + task.getTaskStatus().name());
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        String oldTaskName = task.getTaskName();
        String oldGroupName = task.getTaskGroupName();

        if (newTaskName != null && !newTaskName.isBlank() && !newTaskName.equals(oldTaskName)) {
            task.setTaskName(newTaskName.trim());
            changes.put("taskName", Map.of("old", oldTaskName, "new", task.getTaskName()));
        }
        if (newTaskGroupName != null && !newTaskGroupName.isBlank() && !newTaskGroupName.equals(oldGroupName)) {
            task.setTaskGroupName(newTaskGroupName.trim());
            changes.put("taskGroupName", Map.of("old", oldGroupName, "new", task.getTaskGroupName()));
        }

        if (changes.isEmpty()) {
            return task;
        }

        Task saved = save(task);

        auditLogger.log(user, AuditActionType.edit,
                task.getRequest().getReleaseFlow().getId(),
                task.getRequest().getId(),
                taskId,
                Map.of("fieldsChanged", changes));

        return saved;
    }

    /**
     * Change the execution type of a task (MANUAL ↔ AUTO).
     * Only allowed in Pending or Ready_For_Execution states.
     */
    @Transactional
    public Task editExecutionType(String taskId, ExecutionType newType, UserContext user) {
        Task task = getById(taskId);
        assertTaskRequestActive(task);
        taskPermissionService.assertOwnerOrAdmin(task, user, "task:editExecutionType");

        if (task.getTaskStatus() != TaskStatus.Pending
                && task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            throw new ValidationAppException(
                    "Execution type can only be changed in Pending or Ready_For_Execution states. "
                    + "Current state: " + task.getTaskStatus().name());
        }

        ExecutionType oldType = task.getExecutionType();
        if (oldType == newType) {
            return task;
        }

        task.setExecutionType(newType);
        Task saved = save(task);

        auditLogger.log(user, AuditActionType.edit,
                task.getRequest().getReleaseFlow().getId(),
                task.getRequest().getId(),
                taskId,
                Map.of("fieldChanged", "executionType",
                       "oldValue", oldType.name(),
                       "newValue", newType.name()));

        return saved;
    }

    /**
     * Start MANUAL execution by transitioning Ready_For_Execution → Executing.
     * Allows owners/admins to begin a manual step without first editing input fields.
     */
    @Transactional
    public Task startManualExecution(String taskId, UserContext user) {
        Task task = getById(taskId);
        assertTaskRequestActive(task);
        taskPermissionService.assertOwnerOrAdmin(task, user, "task:startManualExecution");

        if (task.getExecutionType() != ExecutionType.MANUAL) {
            throw new ConflictAppException(
                    "Task " + taskId + " is not a MANUAL task; cannot start manual execution");
        }
        if (task.getTaskStatus() != TaskStatus.Ready_For_Execution) {
            throw new ConflictAppException(
                    "Task must be in Ready_For_Execution state to start, current: "
                            + task.getTaskStatus().name());
        }

        return updateStatus(taskId, TaskStatus.Executing, user, "manual_execution_started");
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

    /**
     * Clone an existing task within the same request.
     * Creates a new copy in Pending status with stepSeq = max(stepSeq within same taskGroupId) + 1.
     */
    @Transactional
    public Task cloneTask(String taskId, UserContext user) {
        Task source = getById(taskId);
        assertTaskRequestActive(source);
        taskPermissionService.assertOwnerOrAdmin(source, user, "task:clone");

        List<Task> siblings = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(
                source.getRequest().getId());
        int maxSeq = siblings.stream()
                .filter(t -> t.getTaskGroupId().equals(source.getTaskGroupId()))
                .mapToInt(Task::getStepSeq)
                .max()
                .orElse(0);

        Task clone = new Task();
        clone.setRequest(source.getRequest());
        clone.setTaskGroupId(source.getTaskGroupId());
        clone.setTaskGroupName(source.getTaskGroupName());
        clone.setStepSeq(maxSeq + 1);
        clone.setTaskName(source.getTaskName() + " (copy)");
        clone.setExecutionType(source.getExecutionType());
        clone.setCritical(source.isCritical());
        clone.setTaskStatus(TaskStatus.Pending);
        clone.setInputParameters(source.getInputParameters() != null
                ? new LinkedHashMap<>(source.getInputParameters()) : null);
        clone.setExpectedOutput(source.getExpectedOutput());
        clone.setOwner(source.getOwner());
        clone.setPlannedStartTime(source.getPlannedStartTime());
        clone.setPlannedEndTime(source.getPlannedEndTime());
        clone.setImportMetadata(source.getImportMetadata() != null
                ? new LinkedHashMap<>(source.getImportMetadata()) : null);
        clone.setCurrentResultSummary(null);
        clone.setLatestExecutionId(null);
        clone.setStartTime(null);
        clone.setEndTime(null);

        Task saved = save(clone);

        auditLogger.log(user, AuditActionType.edit,
                source.getRequest().getReleaseFlow().getId(),
                source.getRequest().getId(),
                saved.getId(),
                Map.of("action", "clone",
                       "sourceTaskId", taskId,
                       "sourceTaskName", source.getTaskName()));

        return saved;
    }

    /**
     * Reorder tasks within a request by updating stepSeq values.
     * Accepts an ordered list of task IDs; assigns stepSeq = 1, 2, 3, ... in that order.
     */
    @Transactional
    public List<Task> reorderTasks(String requestId, List<String> orderedTaskIds, UserContext user) {
        List<Task> tasks = taskRepository.findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(requestId);

        if (tasks.isEmpty()) {
            throw new NotFoundAppException("Request tasks", requestId);
        }
        assertTaskRequestActive(tasks.get(0));

        Map<String, Task> taskMap = new LinkedHashMap<>();
        for (Task task : tasks) {
            taskMap.put(task.getId(), task);
        }

        if (orderedTaskIds.size() != tasks.size()) {
            throw new ValidationAppException(
                    "Reorder list size (" + orderedTaskIds.size() + ") does not match task count ("
                            + tasks.size() + ")");
        }

        List<Task> result = new java.util.ArrayList<>();
        int seq = 1;
        for (String id : orderedTaskIds) {
            Task task = taskMap.get(id);
            if (task == null) {
                throw new ValidationAppException("Task " + id + " not found in request " + requestId);
            }
            task.setStepSeq(seq++);
            result.add(save(task));
        }

        auditLogger.log(user, AuditActionType.edit,
                tasks.get(0).getRequest().getReleaseFlow().getId(),
                requestId,
                null,
                Map.of("action", "reorder",
                       "newOrder", orderedTaskIds));

        return result;
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
