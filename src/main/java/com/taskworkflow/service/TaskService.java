package com.taskworkflow.service;

import com.taskworkflow.domain.Task;
import com.taskworkflow.domain.TaskStatus;
import com.taskworkflow.exception.InvalidTaskStateException;
import com.taskworkflow.exception.TaskNotFoundException;
import com.taskworkflow.git.GitService;
import com.taskworkflow.messaging.Exchanges;
import com.taskworkflow.messaging.RoutingKeys;
import com.taskworkflow.repository.TaskRepository;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final Set<TaskStatus> REPROCESSABLE = EnumSet.of(
            TaskStatus.CREATED, TaskStatus.IN_DEV, TaskStatus.IN_REVIEW, TaskStatus.IN_TEST, TaskStatus.RETRYING);

    private static final Set<TaskStatus> TERMINAL = EnumSet.of(
            TaskStatus.COMPLETED, TaskStatus.DEAD, TaskStatus.CANCELLED);

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

    @Transactional(readOnly = true)
    public List<Task> listTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void deleteAllTasks() {
        long count = taskRepository.count();
        taskRepository.deleteAllInBatch();
        log.info("Todas as tarefas removidas. count={}", count);
    }

    @Transactional
    public Task pauseTask(UUID id) {
        Task task = getTask(id);
        if (TERMINAL.contains(task.getStatus()) || task.getStatus() == TaskStatus.PAUSED) {
            throw new InvalidTaskStateException(id, task.getStatus(), "pausar");
        }
        task.setPausedFromStatus(task.getStatus());
        task.setStatus(TaskStatus.PAUSED);
        taskRepository.save(task);
        log.info("Tarefa pausada. taskId={}, estavaEm={}", task.getId(), task.getPausedFromStatus());
        return task;
    }

    @Transactional
    public Task resumeTask(UUID id) {
        Task task = getTask(id);
        if (task.getStatus() != TaskStatus.PAUSED) {
            throw new InvalidTaskStateException(id, task.getStatus(), "retomar");
        }
        TaskStatus target = task.getPausedFromStatus();
        task.setStatus(target);
        task.setPausedFromStatus(null);
        taskRepository.save(task);
        publishForStatus(task);
        log.info("Tarefa retomada. taskId={}, status={}", task.getId(), target);
        return task;
    }

    @Transactional
    public Task cancelTask(UUID id) {
        Task task = getTask(id);
        if (TERMINAL.contains(task.getStatus())) {
            throw new InvalidTaskStateException(id, task.getStatus(), "cancelar");
        }
        if (task.getStatus() != TaskStatus.PAUSED) {
            task.setPausedFromStatus(task.getStatus());
        }
        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.save(task);
        log.info("Tarefa cancelada. taskId={}", task.getId());
        return task;
    }

    /** Ação manual de recuperação; pode duplicar processamento se a tarefa já estiver ativa de verdade. */
    @Transactional
    public int reprocessStuckTasks() {
        List<Task> stuck = taskRepository.findAllByStatusIn(REPROCESSABLE);
        for (Task task : stuck) {
            publishForStatus(task);
        }
        log.info("Reprocessamento solicitado. tarefas={}", stuck.size());
        return stuck.size();
    }

    private void publishForStatus(Task task) {
        switch (task.getStatus()) {
            case CREATED -> taskPublisher.publish(Exchanges.TASK, RoutingKeys.TASK_CREATED, task);
            case IN_DEV -> taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.TASK_RETRY, task);
            case IN_REVIEW -> taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.DEVELOPMENT_COMPLETED, task);
            case IN_TEST -> taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.REVIEW_APPROVED, task);
            case RETRYING -> taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.REVIEW_REJECTED, task);
            default -> log.warn("Nada a reenviar para o estado {}. taskId={}", task.getStatus(), task.getId());
        }
    }
}
