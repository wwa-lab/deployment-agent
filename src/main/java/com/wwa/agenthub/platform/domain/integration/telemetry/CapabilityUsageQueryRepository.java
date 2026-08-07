package com.wwa.agenthub.platform.domain.integration.telemetry;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.dto.integration.CapabilityUsageDto;
import com.wwa.agenthub.contracts.enums.CapabilityType;
import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CapabilityUsageQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Facts aggregate(
            CapabilityUsageDto.Filters filters,
            IntegrationActor actor,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        QueryParts parts = where(filters, actor, fromInclusive, toExclusive);
        String aggregateJpql = """
                SELECT h.capabilityType, h.capabilityId, COUNT(h),
                    SUM(CASE WHEN h.executionStatus = :completed THEN 1 ELSE 0 END),
                    SUM(CASE WHEN h.executionStatus IN :failedStatuses THEN 1 ELSE 0 END),
                    SUM(CASE WHEN h.executionStatus = :cancelled THEN 1 ELSE 0 END),
                    SUM(CASE WHEN h.executionStatus = :running THEN 1 ELSE 0 END),
                    AVG(CASE WHEN h.executionStatus IN :terminalStatuses THEN h.durationMs ELSE NULL END),
                    COUNT(DISTINCT h.userId)
                FROM TaskExecutionHistory h
                """ + parts.whereClause() + """
                GROUP BY h.capabilityType, h.capabilityId
                ORDER BY COUNT(h) DESC, h.capabilityId ASC
                """;
        Query aggregateQuery = entityManager.createQuery(aggregateJpql);
        setParameters(aggregateQuery, parts.parameters());
        aggregateQuery.setParameter("completed", ExecutionStatus.Completed);
        aggregateQuery.setParameter("failedStatuses", List.of(
                ExecutionStatus.Failed, ExecutionStatus.Timed_Out));
        aggregateQuery.setParameter("cancelled", ExecutionStatus.Cancelled);
        aggregateQuery.setParameter("running", ExecutionStatus.Running);
        aggregateQuery.setParameter("terminalStatuses", List.of(
                ExecutionStatus.Completed, ExecutionStatus.Failed, ExecutionStatus.Timed_Out));

        @SuppressWarnings("unchecked")
        List<Object[]> aggregateResults = aggregateQuery.getResultList();
        List<Aggregate> aggregates = aggregateResults.stream()
                .map(row -> new Aggregate(
                        (CapabilityType) row[0],
                        (String) row[1],
                        longValue(row[2]),
                        longValue(row[3]),
                        longValue(row[4]),
                        longValue(row[5]),
                        longValue(row[6]),
                        doubleValue(row[7]),
                        longValue(row[8])))
                .toList();

        String versionJpql = """
                SELECT h.capabilityType, h.capabilityId, h.capabilityVersion, COUNT(h)
                FROM TaskExecutionHistory h
                """ + parts.whereClause() + """
                GROUP BY h.capabilityType, h.capabilityId, h.capabilityVersion
                ORDER BY h.capabilityType ASC, h.capabilityId ASC, h.capabilityVersion ASC
                """;
        Query versionQuery = entityManager.createQuery(versionJpql);
        setParameters(versionQuery, parts.parameters());
        @SuppressWarnings("unchecked")
        List<Object[]> versionResults = versionQuery.getResultList();
        List<VersionCount> versions = versionResults.stream()
                .map(row -> new VersionCount(
                        (CapabilityType) row[0],
                        (String) row[1],
                        (String) row[2],
                        longValue(row[3])))
                .toList();
        return new Facts(aggregates, versions);
    }

    private static QueryParts where(
            CapabilityUsageDto.Filters filters,
            IntegrationActor actor,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        List<String> predicates = new ArrayList<>();
        Map<String, Object> parameters = new LinkedHashMap<>();
        predicates.add("h.integrationManaged = true");
        predicates.add("h.startTime >= :fromInclusive");
        predicates.add("h.startTime < :toExclusive");
        parameters.put("fromInclusive", fromInclusive);
        parameters.put("toExclusive", toExclusive);

        String selectedCapability = filters.skillId() != null
                ? filters.skillId() : filters.capabilityId();
        addOptional(predicates, parameters, "h.capabilityId", "capabilityId", selectedCapability);
        if (filters.skillId() != null) {
            predicates.add("h.capabilityType = :skillType");
            parameters.put("skillType", CapabilityType.SKILL);
        }
        addOptional(predicates, parameters, "h.configSnowGroup", "team", filters.team());
        addOptional(predicates, parameters, "h.projectId", "projectId", filters.projectId());
        addOptional(predicates, parameters, "h.configAgent", "agent", filters.agent());
        if (filters.clientType() != null) {
            predicates.add("h.clientType = :clientType");
            parameters.put("clientType", filters.clientType());
        }

        if (!actor.allowedAgents().contains(AccessScope.WILDCARD)) {
            if (actor.allowedAgents().isEmpty()) {
                predicates.add("1 = 0");
            } else {
                predicates.add("h.configAgent IN :allowedAgents");
                parameters.put("allowedAgents", actor.allowedAgents());
            }
        }
        addScopePredicate(predicates, parameters, actor);
        return new QueryParts("WHERE " + String.join(" AND ", predicates) + " ", parameters);
    }

    private static void addScopePredicate(
            List<String> predicates,
            Map<String, Object> parameters,
            IntegrationActor actor
    ) {
        if (actor.user().isGlobalDevOpsAdmin()) {
            return;
        }
        List<String> scopePredicates = new ArrayList<>();
        int index = 0;
        for (AccessScope scope : actor.user().scopes()) {
            List<String> parts = new ArrayList<>();
            if (!AccessScope.WILDCARD.equals(scope.application())) {
                parts.add("h.configApplication = :scopeApplication" + index);
                parameters.put("scopeApplication" + index, scope.application());
            }
            if (!AccessScope.WILDCARD.equals(scope.snowGroup())) {
                parts.add("h.configSnowGroup = :scopeSnowGroup" + index);
                parameters.put("scopeSnowGroup" + index, scope.snowGroup());
            }
            if (parts.isEmpty()) {
                return;
            }
            scopePredicates.add("(" + String.join(" AND ", parts) + ")");
            index++;
        }
        predicates.add(scopePredicates.isEmpty()
                ? "1 = 0"
                : "(" + String.join(" OR ", scopePredicates) + ")");
    }

    private static void addOptional(
            List<String> predicates,
            Map<String, Object> parameters,
            String field,
            String parameter,
            Object value
    ) {
        if (value != null) {
            predicates.add(field + " = :" + parameter);
            parameters.put(parameter, value);
        }
    }

    private static void setParameters(Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private static long longValue(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static double doubleValue(Object value) {
        return value == null ? 0 : ((Number) value).doubleValue();
    }

    public record Facts(List<Aggregate> aggregates, List<VersionCount> versions) {
        public Facts {
            aggregates = List.copyOf(aggregates);
            versions = List.copyOf(versions);
        }
    }

    public record Aggregate(
            CapabilityType capabilityType,
            String capabilityId,
            long invocationCount,
            long successCount,
            long failureCount,
            long cancelledCount,
            long runningCount,
            double averageDurationMs,
            long userCount
    ) {
    }

    public record VersionCount(
            CapabilityType capabilityType,
            String capabilityId,
            String version,
            long count
    ) {
    }

    private record QueryParts(String whereClause, Map<String, Object> parameters) {
    }
}
