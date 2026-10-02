package com.taskworkflow.repository;

import com.taskworkflow.domain.Task;
import com.taskworkflow.domain.TaskStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByCorrelationId(String correlationId);

    List<Task> findAllByOrderByCreatedAtDesc();

    List<Task> findAllByStatusIn(Collection<TaskStatus> statuses);
}
