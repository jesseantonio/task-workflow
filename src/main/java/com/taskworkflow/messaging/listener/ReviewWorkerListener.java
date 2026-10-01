package com.taskworkflow.messaging.listener;

import com.rabbitmq.client.Channel;
import com.taskworkflow.ai.TaskAiService;
import com.taskworkflow.config.TaskWorkflowProperties;
import com.taskworkflow.domain.Task;
import com.taskworkflow.domain.TaskStatus;
import com.taskworkflow.git.GitService;
import com.taskworkflow.messaging.Exchanges;
import com.taskworkflow.messaging.Queues;
import com.taskworkflow.messaging.RoutingKeys;
import com.taskworkflow.messaging.event.TaskMessage;
import com.taskworkflow.repository.TaskRepository;
import com.taskworkflow.service.TaskPublisher;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Consome review.queue e decide entre review.approved e review.rejected. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewWorkerListener {

    private final TaskRepository taskRepository;
    private final TaskPublisher taskPublisher;
    private final ListenerAckSupport ackSupport;
    private final TaskWorkflowProperties properties;
    private final TaskAiService taskAiService;
    private final GitService gitService;

    @RabbitListener(queues = Queues.REVIEW)
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
        if (task.getStatus() != TaskStatus.IN_REVIEW) {
            log.warn("Estado inválido para revisão, ignorando. taskId={}, status={}", task.getId(), task.getStatus());
            return;
        }

        boolean approved;
        if (properties.ai().enabled()) {
            String implementationText = task.getRepositoryPath() != null
                    ? gitService.stageAndDiff(Path.of(task.getWorktreePath()))
                    : task.getDevelopmentOutput();
            TaskAiService.ReviewResult result = taskAiService.review(task.getName(), implementationText);
            task.setReviewFeedback(result.feedback());
            approved = result.approved();
        } else {
            approved = ThreadLocalRandom.current().nextDouble() < properties.simulation().reviewApprovalRate();
        }

        if (approved) {
            task.transitionTo(TaskStatus.IN_TEST, TaskStatus.IN_REVIEW);
            taskRepository.save(task);
            taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.REVIEW_APPROVED, task);
            log.info("Revisão aprovada. taskId={}", task.getId());
        } else {
            task.transitionTo(TaskStatus.RETRYING, TaskStatus.IN_REVIEW);
            taskRepository.save(task);
            taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.REVIEW_REJECTED, task);
            log.info("Revisão rejeitada. taskId={}", task.getId());
        }
    }
}
