package com.wwa.agenthub.domain.task;

import com.wwa.agenthub.contracts.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    /**
     * List all tasks for a request, ordered by (taskGroupId, stepSeq) for consistent display.
     */
    List<Task> findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(String requestId);

    List<Task> findByRequestIdAndTaskStatus(String requestId, TaskStatus taskStatus);

    /** Find a specific task by request + template coordinates – used during import upsert. */
    Optional<Task> findByRequestIdAndTaskGroupIdAndStepSeq(String requestId, String taskGroupId, Integer stepSeq);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"request", "request.releaseFlow"})
    @Query("SELECT t FROM Task t WHERE t.id = :taskId")
    Optional<Task> findByIdForExecutionUpdate(@Param("taskId") String taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"request", "request.releaseFlow"})
    @Query("""
        SELECT t FROM Task t
        WHERE EXISTS (
            SELECT e.id FROM TaskExecutionHistory e
            WHERE e.task = t
              AND e.id = :executionId
              AND e.integrationManaged = true
        )
        """)
    Optional<Task> findByIntegrationExecutionIdForUpdate(@Param("executionId") String executionId);

    @EntityGraph(attributePaths = {"request", "request.releaseFlow"})
    @Query("""
        SELECT t FROM Task t
        WHERE t.assigneeUserId IS NOT NULL
          AND t.capabilityType IS NOT NULL
          AND t.capabilityId IS NOT NULL
          AND t.capabilityVersion IS NOT NULL
        ORDER BY t.lastUpdatedAt DESC, t.id ASC
        """)
    List<Task> findIntegrationReadyTasks(Pageable pageable);

    @EntityGraph(attributePaths = {"request", "request.releaseFlow"})
    @Query("SELECT t FROM Task t WHERE t.id = :taskId")
    Optional<Task> findIntegrationTaskById(@Param("taskId") String taskId);
}
