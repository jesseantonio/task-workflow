package com.taskworkflow.messaging.listener;

import com.rabbitmq.client.Channel;
import com.taskworkflow.domain.Task;
import com.taskworkflow.git.GitOperationException;
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

/**
 * Consome completed.queue. Em modo repositório real, aplica o patch (sem commit) no diretório
 * de trabalho do repositório principal e, como backup, commita o resultado numa branch isolada.
 * Nunca dá push nem commita/mergeia na branch principal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletedMonitorListener {

    private final TaskRepository taskRepository;
    private final ListenerAckSupport ackSupport;
    private final GitService gitService;

    @RabbitListener(queues = Queues.COMPLETED)
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
            Path worktreePath = Path.of(task.getWorktreePath());
            Path repositoryPath = Path.of(task.getRepositoryPath());
            String diff = gitService.stageAndDiff(worktreePath);

            try {
                gitService.applyPatch(repositoryPath, diff);
                log.info("Implementação aplicada (sem commit) no diretório de trabalho. taskId={}, repositoryPath={}", task.getId(), repositoryPath);
            } catch (GitOperationException e) {
                log.warn("Não deu pra aplicar a implementação direto no diretório de trabalho (provavelmente ele mudou desde a criação do worktree). "
                        + "Use a branch {} manualmente. taskId={}, motivo={}", task.getBranchName(), task.getId(), e.getMessage());
            }

            gitService.commitAll(worktreePath, "task-workflow: " + task.getName());
            gitService.removeWorktree(repositoryPath, worktreePath);
            log.info("Registro em branch mantido como backup. taskId={}, branch={}", task.getId(), task.getBranchName());
        }
        log.info("Tarefa concluída com sucesso. taskId={}, attempts={}", task.getId(), task.getAttempts());
    }
}
