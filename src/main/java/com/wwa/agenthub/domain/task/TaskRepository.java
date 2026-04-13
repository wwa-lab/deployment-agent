package com.wwa.agenthub.domain.task;

import com.wwa.agenthub.contracts.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    /**
     * List all tasks for a request, ordered by (taskGroupId, stepSeq) for consistent display.
     */
    List<Task> findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(String requestId);

    List<Task> findByRequestIdAndTaskStatus(String requestId, TaskStatus taskStatus);

    /** Find a specific task by request + template coordinates – used during import upsert. */
    Optional<Task> findByRequestIdAndTaskGroupIdAndStepSeq(String requestId, String taskGroupId, Integer stepSeq);
}
