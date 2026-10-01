package com.taskworkflow.repository;

import com.taskworkflow.domain.Task;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByCorrelationId(String correlationId);
}
