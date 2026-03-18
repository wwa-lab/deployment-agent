package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    /**
     * List all tasks for a request, ordered by (taskGroupId, stepSeq) for consistent display.
     */
    List<Task> findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(String requestId);

    List<Task> findByRequestIdAndTaskStatus(String requestId, TaskStatus taskStatus);
}
