package com.wwa.agenthub.platform.domain.integration.auth;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.PermissionKey;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.domain.task.TaskExecutionHistory;
import com.wwa.agenthub.platform.domain.StagePipelineRegistry;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.domain.integration.SensitiveTextRedactor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IntegrationAuthorizationService {

    private static final Pattern COMMIT = Pattern.compile("(?:[a-f0-9]{40}|[a-f0-9]{64})");
    private static final Pattern RESOURCE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SSH_USER_INFO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final StagePipelineRegistry stagePipelineRegistry;

    public boolean isIntegrationReady(Task task) {
        if (task == null || task.getRequest() == null || task.getRequest().getReleaseFlow() == null) {
            return false;
        }
        Request request = task.getRequest();
        boolean coreBinding = safeText(task.getAssigneeUserId(), 255)
                && task.getCapabilityType() != null
                && safeText(task.getCapabilityId(), 255)
                && safeText(task.getCapabilityVersion(), 128)
                && safeText(request.getApplication(), 255)
                && safeText(request.getSnowGroup(), 255)
                && resourceId(request.getReleaseFlow().getProjectId())
                && safeText(request.getAgent(), 128)
                && stagePipelineRegistry.contains(request.getAgent());
        if (!coreBinding) {
            return false;
        }
        boolean noRepository = !notBlank(task.getRepositoryId())
                && !notBlank(task.getRepositoryProvider())
                && !notBlank(task.getRepositoryUrl())
                && !notBlank(task.getRepositoryBranch())
                && !notBlank(task.getRepositoryCommit());
        if (task.getCapabilityType() == CapabilityType.MANUAL && noRepository) {
            return true;
        }
        return resourceId(task.getRepositoryId())
                && safeText(task.getRepositoryProvider(), 128)
                && isSafeRepositoryUrl(task.getRepositoryUrl())
                && isSafeBranch(task.getRepositoryBranch())
                && task.getRepositoryCommit() != null
                && COMMIT.matcher(task.getRepositoryCommit()).matches();
    }

    public void assertTaskVisible(Task task, IntegrationActor actor) {
        assertAuthenticated(actor);
        if (!isIntegrationReady(task)) {
            throw hidden("TASK_NOT_FOUND", "Task");
        }
        Request request = task.getRequest();
        UserContext user = actor.user();
        if (isArchived(request)
                && !user.hasPermission(PermissionKey.RELEASE_VIEW_ARCHIVED.value())) {
            throw hidden("TASK_NOT_FOUND", "Task");
        }
        if (!actor.allowsAgent(request.getAgent())
                || !user.hasScopedAccess(request.getApplication(), request.getSnowGroup())) {
            throw hidden("TASK_NOT_FOUND", "Task");
        }
        if (!canSupervise(user) && !Objects.equals(task.getAssigneeUserId(), user.userId())) {
            throw hidden("TASK_NOT_FOUND", "Task");
        }
    }

    public void assertCanStart(Task task, IntegrationActor actor) {
        assertTaskVisible(task, actor);
        assertRegisteredExecutionClient(actor);
        if (isArchived(task.getRequest())) {
            throw IntegrationApiException.conflict(
                    "TASK_NOT_EXECUTABLE",
                    "Archived Tasks cannot be started.",
                    false);
        }
        UserContext user = actor.user();
        if (Objects.equals(task.getAssigneeUserId(), user.userId())
                || canAdminister(user)
                || user.hasPermission(PermissionKey.PLATFORM_EXECUTION_RUN.value())
                || user.hasPermission(PermissionKey.TASK_RUN.value())) {
            return;
        }
        throw IntegrationApiException.forbidden("The current principal cannot start this Task.");
    }

    public void assertExecutionVisible(TaskExecutionHistory execution, IntegrationActor actor) {
        if (execution == null || !execution.isIntegrationManaged()) {
            throw hidden("EXECUTION_NOT_FOUND", "Execution");
        }
        assertExecutionScopeVisible(execution, actor);
        UserContext user = actor.user();
        boolean owner = Objects.equals(execution.getUserId(), user.userId());
        boolean currentAssignee = execution.getTask() != null
                && Objects.equals(execution.getTask().getAssigneeUserId(), user.userId());
        if (!canSupervise(user) && !owner && !currentAssignee) {
            throw hidden("EXECUTION_NOT_FOUND", "Execution");
        }
    }

    public void assertExecutionScopeVisible(TaskExecutionHistory execution, IntegrationActor actor) {
        if (execution == null || !execution.isIntegrationManaged()) {
            throw hidden("EXECUTION_NOT_FOUND", "Execution");
        }
        assertAuthenticated(actor);
        Request request = execution.getTask() == null ? null : execution.getTask().getRequest();
        if (isArchived(request)
                && !actor.user().hasPermission(PermissionKey.RELEASE_VIEW_ARCHIVED.value())) {
            throw hidden("EXECUTION_NOT_FOUND", "Execution");
        }
        if (!actor.allowsAgent(execution.getConfigAgent())
                || !actor.user().hasScopedAccess(
                        execution.getConfigApplication(), execution.getConfigSnowGroup())) {
            throw hidden("EXECUTION_NOT_FOUND", "Execution");
        }
    }

    public void assertCanWriteExecution(TaskExecutionHistory execution, IntegrationActor actor) {
        assertExecutionScopeVisible(execution, actor);
        assertRegisteredExecutionClient(actor);
        UserContext user = actor.user();
        boolean owner = Objects.equals(execution.getUserId(), user.userId())
                && Objects.equals(execution.getClientApplicationId(), actor.clientApplicationId());
        boolean delegated = canAdminister(user)
                || user.hasPermission(PermissionKey.PLATFORM_EXECUTION_DELEGATE.value());
        if (!owner && !delegated) {
            throw IntegrationApiException.forbidden(
                    "The current principal cannot mutate this Execution.");
        }
    }

    public void assertCanReview(TaskExecutionHistory execution, IntegrationActor actor) {
        assertExecutionVisible(execution, actor);
        if (actor.bearerAuthenticated()) {
            throw IntegrationApiException.forbidden("A human Web session is required for review.");
        }
        UserContext user = actor.user();
        if (Objects.equals(execution.getTask().getAssigneeUserId(), user.userId())
                || canAdminister(user)
                || user.hasPermission(PermissionKey.PLATFORM_EXECUTION_REVIEW.value())
                || user.hasPermission(PermissionKey.TASK_REVIEW.value())) {
            return;
        }
        throw IntegrationApiException.forbidden("The current principal cannot review this Execution.");
    }

    public boolean canReview(TaskExecutionHistory execution, IntegrationActor actor) {
        try {
            assertCanReview(execution, actor);
            return true;
        } catch (IntegrationApiException exception) {
            return false;
        }
    }

    public boolean isExecutionVisibleForList(TaskExecutionHistory execution, IntegrationActor actor) {
        try {
            assertExecutionVisible(execution, actor);
            return true;
        } catch (IntegrationApiException exception) {
            return false;
        }
    }

    public boolean canRerun(Task task, IntegrationActor actor) {
        try {
            assertCanRerun(task, actor);
            return true;
        } catch (IntegrationApiException exception) {
            return false;
        }
    }

    public void assertCanRerun(Task task, IntegrationActor actor) {
        assertTaskVisible(task, actor);
        if (actor.bearerAuthenticated()) {
            throw IntegrationApiException.forbidden("A human Web session is required to rerun a Task.");
        }
        UserContext user = actor.user();
        if (Objects.equals(task.getAssigneeUserId(), actor.principalId())
                || canAdminister(user)
                || user.hasPermission(PermissionKey.PLATFORM_EXECUTION_RUN.value())
                || user.hasPermission(PermissionKey.TASK_RUN.value())) {
            return;
        }
        throw IntegrationApiException.forbidden("The current principal cannot rerun this Task.");
    }

    public void assertCanViewTelemetry(IntegrationActor actor) {
        assertAuthenticated(actor);
        UserContext user = actor.user();
        if (canAdminister(user)
                || user.hasRole("AUDIT")
                || user.hasRole("MANAGEMENT")
                || user.hasRole("TL")
                || user.hasPermission(PermissionKey.PLATFORM_TELEMETRY_VIEW.value())) {
            return;
        }
        throw IntegrationApiException.forbidden("Capability usage access is not granted.");
    }

    public boolean isVisibleForList(Task task, IntegrationActor actor) {
        try {
            assertTaskVisible(task, actor);
            return true;
        } catch (IntegrationApiException exception) {
            return false;
        }
    }

    public boolean isExecutionInTelemetryScope(TaskExecutionHistory execution, IntegrationActor actor) {
        if (execution == null || !execution.isIntegrationManaged()) {
            return false;
        }
        return actor.allowsAgent(execution.getConfigAgent())
                && actor.user().hasScopedAccess(
                        execution.getConfigApplication(), execution.getConfigSnowGroup());
    }

    private static void assertRegisteredExecutionClient(IntegrationActor actor) {
        if (!actor.bearerAuthenticated()) {
            throw IntegrationApiException.forbidden(
                    "A registered Integration client is required for Execution commands.");
        }
    }

    private static void assertAuthenticated(IntegrationActor actor) {
        if (actor == null || actor.user() == null || actor.user().isGuestViewer()) {
            throw new IntegrationApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "An authenticated non-guest principal is required.",
                    false);
        }
    }

    private static boolean canSupervise(UserContext user) {
        return canAdminister(user)
                || user.hasRole("TL")
                || user.hasRole("AUDIT")
                || user.hasRole("MANAGEMENT")
                || user.hasPermission(PermissionKey.PLATFORM_EXECUTION_RUN.value())
                || user.hasPermission(PermissionKey.PLATFORM_EXECUTION_REVIEW.value())
                || user.hasPermission(PermissionKey.TASK_REVIEW.value());
    }

    private static boolean canAdminister(UserContext user) {
        return user.hasRole("DEVOPS_ADMIN");
    }

    private static boolean isArchived(Request request) {
        return request != null
                && (request.getArchivedAt() != null
                || (request.getReleaseFlow() != null
                && request.getReleaseFlow().getArchivedAt() != null));
    }

    private static IntegrationApiException hidden(String code, String resource) {
        return IntegrationApiException.notFound(code, resource);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean safeText(String value, int maximumLength) {
        return notBlank(value)
                && value.length() <= maximumLength
                && value.chars().noneMatch(Character::isISOControl)
                && SensitiveTextRedactor.redact(value).equals(value);
    }

    private static boolean resourceId(String value) {
        return value != null
                && RESOURCE_ID.matcher(value).matches()
                && SensitiveTextRedactor.redact(value).equals(value);
    }

    private static boolean isSafeBranch(String value) {
        return notBlank(value)
                && value.length() <= 1024
                && value.chars().noneMatch(Character::isISOControl)
                && SensitiveTextRedactor.redact(value).equals(value);
    }

    private static boolean isSafeRepositoryUrl(String value) {
        if (!notBlank(value) || value.length() > 2048) {
            return false;
        }
        try {
            URI uri = new URI(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "ssh".equalsIgnoreCase(uri.getScheme()))
                    || !notBlank(uri.getHost())
                    || !notBlank(uri.getPath())
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                return false;
            }
            String userInfo = uri.getRawUserInfo();
            return userInfo == null
                    || ("ssh".equalsIgnoreCase(uri.getScheme())
                    && SSH_USER_INFO.matcher(userInfo).matches());
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
