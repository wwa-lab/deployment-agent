package com.wwa.agenthub.domain.task;

import com.wwa.agenthub.contracts.enums.ExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskExecutionHistoryRepository extends JpaRepository<TaskExecutionHistory, String> {

    /** All execution attempts for a task, ordered by attempt number ascending. */
    List<TaskExecutionHistory> findByTaskIdOrderByAttemptNumberAsc(String taskId);

    /** Latest (highest attempt number) execution for a task. */
    Optional<TaskExecutionHistory> findFirstByTaskIdOrderByAttemptNumberDesc(String taskId);

    /** Maximum attempt number for a task; returns 0 if no history exists. */
    @Query("SELECT COALESCE(MAX(h.attemptNumber), 0) FROM TaskExecutionHistory h WHERE h.task.id = :taskId")
    int findMaxAttemptNumberByTaskId(@Param("taskId") String taskId);

    /**
     * Active AUTO executions eligible for polling.
     * Orders by {@code lastSyncedAt} ascending (nulls first) so the most stale rows are refreshed first.
     * Bounded by {@code pageable} to limit batch size per monitor cycle.
     */
    @Query("""
        SELECT h FROM TaskExecutionHistory h
        WHERE h.executionStatus = :status
          AND h.externalSystemType IS NOT NULL
        ORDER BY h.lastSyncedAt ASC NULLS FIRST
        """)
    List<TaskExecutionHistory> findActiveAutoExecutions(
            @Param("status") ExecutionStatus status,
            Pageable pageable);
}
