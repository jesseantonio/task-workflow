package com.taskworkflow.messaging.listener;

import com.rabbitmq.client.Channel;
import com.taskworkflow.ai.TaskAiService;
import com.taskworkflow.config.TaskWorkflowProperties;
import com.taskworkflow.domain.Task;
import com.taskworkflow.domain.TaskStatus;
import com.taskworkflow.git.GitOperationException;
import com.taskworkflow.git.GitService;
import com.taskworkflow.messaging.Exchanges;
import com.taskworkflow.messaging.Queues;
import com.taskworkflow.messaging.RoutingKeys;
import com.taskworkflow.messaging.event.TaskMessage;
import com.taskworkflow.repository.TaskRepository;
import com.taskworkflow.service.TaskPublisher;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Consome dev.queue (task.created e task.retry) e publica development.completed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevelopmentWorkerListener {

    private final TaskRepository taskRepository;
    private final TaskPublisher taskPublisher;
    private final ListenerAckSupport ackSupport;
    private final TaskAiService taskAiService;
    private final TaskWorkflowProperties properties;
    private final GitService gitService;

    @RabbitListener(queues = Queues.DEV)
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
        if (!task.transitionTo(TaskStatus.IN_DEV, TaskStatus.CREATED, TaskStatus.IN_DEV)) {
            log.warn("Estado inválido para desenvolvimento, ignorando. taskId={}, status={}", task.getId(), task.getStatus());
            return;
        }
        taskRepository.save(task);

        if (properties.ai().enabled()) {
            if (task.getRepositoryPath() != null) {
                developInRepository(task);
            } else {
                String previousFeedback = previousFeedback(task);
                TaskAiService.DevelopmentResult result = taskAiService.implement(task.getName(), previousFeedback);
                task.setDevelopmentOutput(result.implementation());
                log.info("Implementação gerada pela IA. taskId={}, resumo={}", task.getId(), result.summary());
            }
        }

        // re-busca: a tarefa pode ter sido pausada/cancelada durante a chamada de IA acima
        String developmentOutput = task.getDevelopmentOutput();
        Task current = taskRepository.findById(task.getId()).orElse(null);
        if (current == null || !current.transitionTo(TaskStatus.IN_REVIEW, TaskStatus.IN_DEV)) {
            log.warn("Tarefa pausada/cancelada/alterada durante o desenvolvimento, descartando resultado. taskId={}", task.getId());
            return;
        }
        current.setDevelopmentOutput(developmentOutput);
        taskRepository.save(current);
        taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.DEVELOPMENT_COMPLETED, current);
        log.info("Desenvolvimento concluído. taskId={}, attempts={}", current.getId(), current.getAttempts());
    }

    private void developInRepository(Task task) {
        if (task.getWorktreePath() == null) {
            Path worktree = gitService.createWorktree(Path.of(task.getRepositoryPath()), task.getBranchName());
            task.setWorktreePath(worktree.toString());
            taskRepository.save(task);
        }
        Path worktreePath = Path.of(task.getWorktreePath());

        TaskAiService.PatchResult result = taskAiService.implementAsPatch(worktreePath, task.getName(), previousFeedback(task));
        try {
            gitService.applyPatch(worktreePath, result.patch());
            task.setDevelopmentOutput(result.summary());
            log.info("Patch aplicado no worktree. taskId={}, branch={}, resumo={}", task.getId(), task.getBranchName(), result.summary());
        } catch (GitOperationException e) {
            task.setDevelopmentOutput("Falha ao aplicar o patch gerado pela IA: " + e.getMessage());
            log.warn("Patch inválido/não aplicável. taskId={}", task.getId(), e);
        }
    }

    private String previousFeedback(Task task) {
        StringBuilder feedback = new StringBuilder();
        if (task.getReviewFeedback() != null) {
            feedback.append("Revisão: ").append(task.getReviewFeedback()).append('\n');
        }
        if (task.getTestReport() != null) {
            feedback.append("Testes: ").append(task.getTestReport());
        }
        return feedback.isEmpty() ? null : feedback.toString();
    }
}
