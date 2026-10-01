package com.taskworkflow.config;

import com.taskworkflow.messaging.Exchanges;
import com.taskworkflow.messaging.Queues;
import com.taskworkflow.messaging.RoutingKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara a topologia do RabbitMQ descrita no README (exchanges, filas e bindings)
 * e configura publisher confirms/returns + entrega persistente por padrão.
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    // Exchanges

    @Bean
    public TopicExchange taskExchange() {
        return new TopicExchange(Exchanges.TASK, true, false);
    }

    @Bean
    public TopicExchange workflowExchange() {
        return new TopicExchange(Exchanges.WORKFLOW, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(Exchanges.DEAD_LETTER, true, false);
    }

    // Filas

    @Bean
    public Queue devQueue() {
        return new Queue(Queues.DEV, true);
    }

    @Bean
    public Queue reviewQueue() {
        return new Queue(Queues.REVIEW, true);
    }

    @Bean
    public Queue testQueue() {
        return new Queue(Queues.TEST, true);
    }

    @Bean
    public Queue retryQueue() {
        return new Queue(Queues.RETRY, true);
    }

    @Bean
    public Queue completedQueue() {
        return new Queue(Queues.COMPLETED, true);
    }

    @Bean
    public Queue dlqQueue() {
        return new Queue(Queues.DLQ, true);
    }

    // Bindings

    @Bean
    public Binding taskCreatedBinding() {
        return BindingBuilder.bind(devQueue()).to(taskExchange()).with(RoutingKeys.TASK_CREATED);
    }

    @Bean
    public Binding developmentCompletedBinding() {
        return BindingBuilder.bind(reviewQueue()).to(workflowExchange()).with(RoutingKeys.DEVELOPMENT_COMPLETED);
    }

    @Bean
    public Binding reviewApprovedBinding() {
        return BindingBuilder.bind(testQueue()).to(workflowExchange()).with(RoutingKeys.REVIEW_APPROVED);
    }

    @Bean
    public Binding reviewRejectedBinding() {
        return BindingBuilder.bind(retryQueue()).to(workflowExchange()).with(RoutingKeys.REVIEW_REJECTED);
    }

    @Bean
    public Binding testSucceededBinding() {
        return BindingBuilder.bind(completedQueue()).to(workflowExchange()).with(RoutingKeys.TEST_SUCCEEDED);
    }

    @Bean
    public Binding testFailedBinding() {
        return BindingBuilder.bind(retryQueue()).to(workflowExchange()).with(RoutingKeys.TEST_FAILED);
    }

    @Bean
    public Binding taskRetryBinding() {
        return BindingBuilder.bind(devQueue()).to(workflowExchange()).with(RoutingKeys.TASK_RETRY);
    }

    @Bean
    public Binding taskDeadBinding() {
        return BindingBuilder.bind(dlqQueue()).to(deadLetterExchange()).with(RoutingKeys.TASK_DEAD);
    }

    // Serialização e confiabilidade de publicação

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setBeforePublishPostProcessors(message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("Publicação não confirmada pelo broker. correlationData={}, causa={}", correlationData, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "Mensagem devolvida pelo broker (sem fila destino). exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}
