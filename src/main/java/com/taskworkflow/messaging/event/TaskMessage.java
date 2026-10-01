package com.taskworkflow.messaging.event;

import java.util.UUID;

/**
 * Envelope publicado em todas as etapas do fluxo. O routing key da mensagem
 * já identifica o evento (task.created, review.approved, etc.); o payload
 * carrega apenas o necessário para o próximo worker localizar a Task no banco.
 */
public record TaskMessage(UUID taskId, String correlationId, int attempts) {
}
