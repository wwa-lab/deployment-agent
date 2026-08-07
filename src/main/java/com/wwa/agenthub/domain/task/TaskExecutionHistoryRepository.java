package com.wwa.agenthub.domain.task;

import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

@Repository
public interface TaskExecutionHistoryRepository extends JpaRepository<TaskExecutionHistory, String> {

    /** All execution attempts for a task, ordered by attempt number ascending. */
    List<TaskExecutionHistory> findByTaskIdOrderByAttemptNumberAsc(String taskId);

    List<TaskExecutionHistory> findByTaskIdOrderByAttemptNumberDesc(String taskId);

    List<TaskExecutionHistory> findByTaskIdAndIntegrationManagedFalseOrderByAttemptNumberAsc(String taskId);

    long countByTaskId(String taskId);

    @EntityGraph(attributePaths = {"task", "task.request", "task.request.releaseFlow"})
    @Query("""
        SELECT history FROM TaskExecutionHistory history
        WHERE history.integrationManaged = true
          AND history.task.id = :taskId
          AND (:attemptNumber IS NULL
            OR history.attemptNumber < :attemptNumber
            OR (history.attemptNumber = :attemptNumber AND history.id < :executionId))
        ORDER BY history.attemptNumber DESC, history.id DESC
        """)
    List<TaskExecutionHistory> findIntegrationHistoryAfter(
            @Param("taskId") String taskId,
            @Param("attemptNumber") Integer attemptNumber,
            @Param("executionId") String executionId,
            Pageable pageable);

    long countByTaskIdAndIntegrationManagedTrue(String taskId);

    Optional<TaskExecutionHistory> findFirstByTaskIdAndIntegrationManagedTrueOrderByAttemptNumberDesc(
            String taskId);

    @EntityGraph(attributePaths = {"task", "task.request", "task.request.releaseFlow"})
    @Query("""
        SELECT history FROM TaskExecutionHistory history
        WHERE history.integrationManaged = true
          AND history.task.id IN :taskIds
          AND history.attemptNumber = (
              SELECT MAX(candidate.attemptNumber)
              FROM TaskExecutionHistory candidate
              WHERE candidate.integrationManaged = true
                AND candidate.task.id = history.task.id
          )
        """)
    List<TaskExecutionHistory> findLatestIntegrationExecutions(
            @Param("taskIds") Set<String> taskIds);

    @Query("""
        SELECT history.task.id AS taskId, COUNT(history) AS executionCount
        FROM TaskExecutionHistory history
        WHERE history.integrationManaged = true
          AND history.task.id IN :taskIds
        GROUP BY history.task.id
        """)
    List<IntegrationExecutionCount> countIntegrationExecutions(
            @Param("taskIds") Set<String> taskIds);

    interface IntegrationExecutionCount {
        String getTaskId();
        long getExecutionCount();
    }

    /** Latest (highest attempt number) execution for a task. */
    Optional<TaskExecutionHistory> findFirstByTaskIdOrderByAttemptNumberDesc(String taskId);

    Optional<TaskExecutionHistory> findFirstByTaskIdAndIntegrationManagedFalseOrderByAttemptNumberDesc(
            String taskId);

    /** Maximum attempt number for a task; returns 0 if no history exists. */
    @Query("SELECT COALESCE(MAX(h.attemptNumber), 0) FROM TaskExecutionHistory h WHERE h.task.id = :taskId")
    int findMaxAttemptNumberByTaskId(@Param("taskId") String taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"task", "task.request", "task.request.releaseFlow"})
    @Query("SELECT h FROM TaskExecutionHistory h WHERE h.id = :executionId")
    Optional<TaskExecutionHistory> findByIdForUpdate(@Param("executionId") String executionId);

    @EntityGraph(attributePaths = {"task", "task.request", "task.request.releaseFlow"})
    @Query("SELECT h FROM TaskExecutionHistory h WHERE h.id = :executionId")
    Optional<TaskExecutionHistory> findIntegrationExecutionById(@Param("executionId") String executionId);

    @EntityGraph(attributePaths = {"task", "task.request", "task.request.releaseFlow"})
    @Query("SELECT h FROM TaskExecutionHistory h WHERE h.integrationManaged = true")
    List<TaskExecutionHistory> findAllIntegrationManaged();

    /**
     * Active AUTO executions eligible for polling.
     * Orders by {@code lastSyncedAt} ascending (nulls first) so the most stale rows are refreshed first.
     * Bounded by {@code pageable} to limit batch size per monitor cycle.
     */
    @Query("""
        SELECT h FROM TaskExecutionHistory h
        WHERE h.executionStatus = :status
          AND h.integrationManaged = false
          AND h.externalSystemType IS NOT NULL
        ORDER BY h.lastSyncedAt ASC NULLS FIRST
        """)
    List<TaskExecutionHistory> findActiveAutoExecutions(
            @Param("status") ExecutionStatus status,
            Pageable pageable);
}
