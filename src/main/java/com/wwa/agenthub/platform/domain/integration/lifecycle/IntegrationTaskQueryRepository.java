package com.wwa.agenthub.platform.domain.integration.lifecycle;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.PermissionKey;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.platform.domain.StagePipelineRegistry;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scope-first query for the Integration Task feed. */
@Repository
public class IntegrationTaskQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final StagePipelineRegistry stagePipelineRegistry;

    public IntegrationTaskQueryRepository(StagePipelineRegistry stagePipelineRegistry) {
        this.stagePipelineRegistry = stagePipelineRegistry;
    }

    public List<Task> find(
            IntegrationActor actor,
            ExecutionLifecycleService.TaskFilters filters,
            ExecutionLifecycleService.TaskCursor cursor,
            int limit
    ) {
        List<String> predicates = new ArrayList<>();
        Map<String, Object> parameters = new LinkedHashMap<>();
        predicates.add("t.assigneeUserId IS NOT NULL");
        predicates.add("t.capabilityType IS NOT NULL");
        predicates.add("t.capabilityId IS NOT NULL");
        predicates.add("t.capabilityVersion IS NOT NULL");
        predicates.add("r.application IS NOT NULL");
        predicates.add("r.snowGroup IS NOT NULL");
        predicates.add("r.agent IS NOT NULL");
        predicates.add("f.projectId IS NOT NULL");
        predicates.add("r.agent IN :registeredAgents");
        parameters.put("registeredAgents", stagePipelineRegistry.registeredAgentIds());
        predicates.add("(t.capabilityType = :manualType OR "
                + "(t.repositoryId IS NOT NULL AND t.repositoryProvider IS NOT NULL "
                + "AND t.repositoryUrl IS NOT NULL AND t.repositoryBranch IS NOT NULL "
                + "AND t.repositoryCommit IS NOT NULL))");
        parameters.put("manualType", CapabilityType.MANUAL);

        if (!actor.user().hasPermission(PermissionKey.RELEASE_VIEW_ARCHIVED.value())) {
            predicates.add("r.archivedAt IS NULL");
            predicates.add("f.archivedAt IS NULL");
        }
        if (!actor.allowedAgents().contains(AccessScope.WILDCARD)) {
            if (actor.allowedAgents().isEmpty()) {
                predicates.add("1 = 0");
            } else {
                predicates.add("r.agent IN :allowedAgents");
                parameters.put("allowedAgents", actor.allowedAgents());
            }
        }
        addScope(predicates, parameters, actor);
        if (!canSupervise(actor)) {
            predicates.add("t.assigneeUserId = :principalId");
            parameters.put("principalId", actor.principalId());
        }
        if (filters != null) {
            if (notBlank(filters.status())) {
                predicates.add("t.taskStatus = :taskStatus");
                parameters.put("taskStatus", TaskStatus.valueOf(normalizeStatus(filters.status())));
            }
            addCaseInsensitive(predicates, parameters, "f.projectId", "projectId", filters.projectId());
            addCaseInsensitive(predicates, parameters, "r.snowGroup", "team", filters.team());
            addCaseInsensitive(predicates, parameters, "r.agent", "agent", filters.agentModuleId());
        }
        if (cursor != null) {
            predicates.add("(t.createdAt < :cursorCreatedAt OR "
                    + "(t.createdAt = :cursorCreatedAt AND t.id > :cursorTaskId))");
            parameters.put("cursorCreatedAt", cursor.createdAt());
            parameters.put("cursorTaskId", cursor.taskId());
        }

        String jpql = "SELECT t FROM Task t JOIN FETCH t.request r JOIN FETCH r.releaseFlow f WHERE "
                + String.join(" AND ", predicates)
                + " ORDER BY t.createdAt DESC, t.id ASC";
        TypedQuery<Task> query = entityManager.createQuery(jpql, Task.class);
        parameters.forEach(query::setParameter);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private static void addScope(
            List<String> predicates,
            Map<String, Object> parameters,
            IntegrationActor actor
    ) {
        if (actor.user().isGlobalDevOpsAdmin()) {
            return;
        }
        List<String> alternatives = new ArrayList<>();
        int index = 0;
        for (AccessScope scope : actor.user().scopes()) {
            List<String> parts = new ArrayList<>();
            if (!AccessScope.WILDCARD.equals(scope.application())) {
                parts.add("r.application = :scopeApplication" + index);
                parameters.put("scopeApplication" + index, scope.application());
            }
            if (!AccessScope.WILDCARD.equals(scope.snowGroup())) {
                parts.add("r.snowGroup = :scopeSnowGroup" + index);
                parameters.put("scopeSnowGroup" + index, scope.snowGroup());
            }
            if (parts.isEmpty()) {
                return;
            }
            alternatives.add("(" + String.join(" AND ", parts) + ")");
            index++;
        }
        predicates.add(alternatives.isEmpty()
                ? "1 = 0"
                : "(" + String.join(" OR ", alternatives) + ")");
    }

    private static void addCaseInsensitive(
            List<String> predicates,
            Map<String, Object> parameters,
            String field,
            String parameter,
            String value
    ) {
        if (notBlank(value)) {
            predicates.add("LOWER(" + field + ") = :" + parameter);
            parameters.put(parameter, value.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static boolean canSupervise(IntegrationActor actor) {
        return actor.user().hasRole("DEVOPS_ADMIN")
                || actor.user().hasRole("TL")
                || actor.user().hasRole("AUDIT")
                || actor.user().hasRole("MANAGEMENT")
                || actor.user().hasPermission(PermissionKey.PLATFORM_EXECUTION_RUN.value())
                || actor.user().hasPermission(PermissionKey.PLATFORM_EXECUTION_REVIEW.value())
                || actor.user().hasPermission(PermissionKey.TASK_REVIEW.value());
    }

    private static String normalizeStatus(String value) {
        return java.util.Arrays.stream(TaskStatus.values())
                .map(TaskStatus::name)
                .filter(status -> status.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
