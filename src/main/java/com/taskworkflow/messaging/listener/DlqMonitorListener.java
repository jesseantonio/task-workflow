package com.taskworkflow.messaging.listener;

import com.rabbitmq.client.Channel;
import com.taskworkflow.domain.Task;
import com.taskworkflow.git.GitService;
import com.taskworkflow.messaging.Queues;
import com.taskworkflow.messaging.event.TaskMessage;
import com.taskworkflow.repository.TaskRepository;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Consome task.dlq; em modo repositório real, descarta o worktree/branch da tentativa fracassada. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqMonitorListener {

    private final TaskRepository taskRepository;
    private final ListenerAckSupport ackSupport;
    private final GitService gitService;

    @RabbitListener(queues = Queues.DLQ)
    public void onMessage(@Payload TaskMessage message,
                           @Header(AmqpHeaders.CHANNEL) Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        ackSupport.process(channel, deliveryTag, message.correlationId(), () -> handle(message));
    }

    private void handle(TaskMessage message) {
        Task task = taskRepository.findById(message.taskId()).orElse(null);
        if (task == null) {
            log.warn("Task não encontrada, ignorando mensagem. taskId={}", message.taskId());
            return;
        }
        if (task.getRepositoryPath() != null && task.getWorktreePath() != null) {
            Path repositoryPath = Path.of(task.getRepositoryPath());
            gitService.removeWorktree(repositoryPath, Path.of(task.getWorktreePath()));
            gitService.deleteBranch(repositoryPath, task.getBranchName());
        }
        log.warn("Tarefa morta após esgotar tentativas. taskId={}, attempts={}", task.getId(), task.getAttempts());
    }
}
