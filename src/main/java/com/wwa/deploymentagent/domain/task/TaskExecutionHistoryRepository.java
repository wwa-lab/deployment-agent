package com.wwa.deploymentagent.domain.task;

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
}
