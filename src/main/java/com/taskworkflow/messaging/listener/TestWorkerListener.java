package com.taskworkflow.messaging.listener;

import com.rabbitmq.client.Channel;
import com.taskworkflow.ai.TaskAiService;
import com.taskworkflow.ai.TestCommandRunner;
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

/** Consome test.queue e decide entre test.succeeded e test.failed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestWorkerListener {

    private final TaskRepository taskRepository;
    private final TaskPublisher taskPublisher;
    private final ListenerAckSupport ackSupport;
    private final TaskWorkflowProperties properties;
    private final TaskAiService taskAiService;
    private final GitService gitService;
    private final TestCommandRunner testCommandRunner;

    @RabbitListener(queues = Queues.TEST)
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
        if (task.getStatus() != TaskStatus.IN_TEST) {
            log.warn("Estado inválido para testes, ignorando. taskId={}, status={}", task.getId(), task.getStatus());
            return;
        }

        boolean succeeded;
        if (properties.ai().enabled() && task.getRepositoryPath() != null && hasTestCommand(task)) {
            TestCommandRunner.Result result = testCommandRunner.run(
                    Path.of(task.getWorktreePath()), task.getTestCommand(), properties.ai().timeoutSeconds());
            task.setTestReport(result.output());
            succeeded = result.succeeded();
            log.info("Comando de teste real executado. taskId={}, command={}, succeeded={}", task.getId(), task.getTestCommand(), succeeded);
        } else if (properties.ai().enabled()) {
            String implementationText = task.getRepositoryPath() != null
                    ? gitService.stageAndDiff(Path.of(task.getWorktreePath()))
                    : task.getDevelopmentOutput();
            TaskAiService.TestResult result = taskAiService.test(task.getName(), implementationText);
            task.setTestReport(result.report());
            succeeded = result.succeeded();
        } else {
            succeeded = ThreadLocalRandom.current().nextDouble() < properties.simulation().testSuccessRate();
        }

        if (succeeded) {
            task.transitionTo(TaskStatus.COMPLETED, TaskStatus.IN_TEST);
            taskRepository.save(task);
            taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.TEST_SUCCEEDED, task);
            log.info("Testes concluídos com sucesso. taskId={}", task.getId());
        } else {
            task.transitionTo(TaskStatus.RETRYING, TaskStatus.IN_TEST);
            taskRepository.save(task);
            taskPublisher.publish(Exchanges.WORKFLOW, RoutingKeys.TEST_FAILED, task);
            log.info("Testes falharam. taskId={}", task.getId());
        }
    }

    private boolean hasTestCommand(Task task) {
        return task.getTestCommand() != null && !task.getTestCommand().isBlank();
    }
}
