package com.taskworkflow.service;

import com.taskworkflow.domain.Task;
import com.taskworkflow.messaging.CorrelationIdSupport;
import com.taskworkflow.messaging.event.TaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publica eventos propagando o correlationId da Task. Se há uma transação ativa, adia o envio
 * para depois do commit (senão um consumidor pode ler a Task do banco antes dela existir).
 */
@Component
@RequiredArgsConstructor
public class TaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String exchange, String routingKey, Task task) {
        TaskMessage message = new TaskMessage(task.getId(), task.getCorrelationId(), task.getAttempts());
        Runnable send = () -> rabbitTemplate.convertAndSend(exchange, routingKey, message, m -> {
            m.getMessageProperties().setCorrelationId(task.getCorrelationId());
            m.getMessageProperties().setHeader(CorrelationIdSupport.HEADER, task.getCorrelationId());
            return m;
        });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
