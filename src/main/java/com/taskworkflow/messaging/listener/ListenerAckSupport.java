package com.taskworkflow.messaging.listener;

import com.rabbitmq.client.Channel;
import com.taskworkflow.messaging.CorrelationIdSupport;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Centraliza o ciclo de ack/nack manual (spring.rabbitmq.listener.simple.acknowledge-mode=manual)
 * e a propagação do correlationId para o MDC do logger, comum a todos os listeners do fluxo.
 */
@Slf4j
@Component
public class ListenerAckSupport {

    public void process(Channel channel, long deliveryTag, String correlationId, Runnable action) {
        CorrelationIdSupport.put(correlationId);
        try {
            action.run();
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Falha ao processar mensagem, enviando para nack sem requeue", e);
            nack(channel, deliveryTag);
        } finally {
            CorrelationIdSupport.clear();
        }
    }

    private void nack(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException ioException) {
            log.error("Falha ao enviar nack para o broker", ioException);
        }
    }
}
