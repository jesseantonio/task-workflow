package com.taskworkflow.service;

import com.taskworkflow.domain.Task;
import com.taskworkflow.exception.TaskNotFoundException;
import com.taskworkflow.git.GitService;
import com.taskworkflow.messaging.Exchanges;
import com.taskworkflow.messaging.RoutingKeys;
import com.taskworkflow.repository.TaskRepository;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskPublisher taskPublisher;
    private final GitService gitService;

    @Transactional
    public Task createTask(String name, String repositoryPath, String testCommand) {
        Task task = new Task(name);
        if (repositoryPath != null && !repositoryPath.isBlank()) {
            Path path = Path.of(repositoryPath);
            gitService.validateRepository(path);
            task.setRepositoryPath(path.toString());
            task.setBranchName("task-workflow/" + task.getCorrelationId());
            task.setTestCommand(testCommand);
        }
        // id pré-atribuído faz o Spring Data usar merge(), que retorna uma instância separada.
        task = taskRepository.save(task);
        taskPublisher.publish(Exchanges.TASK, RoutingKeys.TASK_CREATED, task);
        log.info("Tarefa criada. taskId={}, correlationId={}, repositoryPath={}", task.getId(), task.getCorrelationId(), task.getRepositoryPath());
        return task;
    }

    @Transactional(readOnly = true)
    public Task getTask(UUID id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }
}
