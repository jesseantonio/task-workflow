package com.taskworkflow.messaging.listener;

import com.rabbitmq.client.Channel;
import com.taskworkflow.config.TaskWorkflowProperties;
import com.taskworkflow.domain.Task;
import com.taskworkflow.domain.TaskStatus;
import com.taskworkflow.messaging.Exchanges;
import com.taskworkflow.messaging.Queues;
import com.taskworkflow.messaging.RoutingKeys;
import com.taskworkflow.messaging.event.TaskMessage;
import com.taskworkflow.repository.TaskRepository;
import com.taskworkflow.service.TaskPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consome retry.queue (review.rejected e test.failed), incrementa o contador de
 * tentativas e decide entre reenviar para desenvolvimento (task.retry) ou
 * encaminhar para a DLQ (task.dead), conforme task-workflow.max-attempts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryHandlerListener {

    private final TaskRepository taskRepository;
    private final TaskPublisher taskPublisher;
    private final ListenerAckSupport ackSupport;
    private final TaskWorkflowProperties properties;

    @RabbitListener(queues = Queues.RETRY)
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
        if (task.getStatus() != TaskStatus.RETRYING) {
            log.warn("Estado inválido para retry, ignorando. taskId={}, status={}", task.getId(), task.getStatus());
            return;
        }

        task.setAttempts(task.getAttempts() + 1);
        if (task.getAttempts() < properties.maxAttempts()) {
            task.transitionTo(TaskStatus.IN_DEV, TaskStatus.RETRYING);
            taskRepository.save(task);
            taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.TASK_RETRY, task);
            log.info("Reenviando tarefa para desenvolvimento. taskId={}, attempts={}", task.getId(), task.getAttempts());
        } else {
            task.transitionTo(TaskStatus.DEAD, TaskStatus.RETRYING);
            taskRepository.save(task);
            taskPublisher.publish(Exchanges.DEAD_LETTER, RoutingKeys.TASK_DEAD, task);
            log.warn("Tentativas esgotadas, enviando para DLQ. taskId={}, attempts={}", task.getId(), task.getAttempts());
        }
    }
}
