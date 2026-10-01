package com.taskworkflow.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parâmetros que controlam a simulação de processamento de cada etapa do fluxo
 * e o número máximo de tentativas antes de uma tarefa ir para a DLQ.
 */
@Validated
@ConfigurationProperties(prefix = "task-workflow")
public record TaskWorkflowProperties(
        @Min(1) int maxAttempts,
        Simulation simulation,
        Ai ai) {

    public record Simulation(
            @DecimalMin("0.0") @DecimalMax("1.0") double reviewApprovalRate,
            @DecimalMin("0.0") @DecimalMax("1.0") double testSuccessRate) {
    }

    /**
     * Integração opcional com o Claude Code CLI (claude -p) para que Development/Review/Test
     * Worker usem uma IA real em vez da simulação aleatória. Requer o CLI instalado e
     * autenticado na máquina onde o serviço roda (ver README).
     */
    public record Ai(
            boolean enabled,
            String model,
            @Min(1) int timeoutSeconds,
            @DecimalMin("0.0") double maxBudgetUsd) {
    }
}
