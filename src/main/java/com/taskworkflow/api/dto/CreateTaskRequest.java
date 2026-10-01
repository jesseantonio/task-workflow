package com.taskworkflow.api.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code repositoryPath}/{@code testCommand} só têm efeito com task-workflow.ai.enabled=true. */
public record CreateTaskRequest(
        @NotBlank(message = "não pode ser vazio") String name,
        String repositoryPath,
        String testCommand) {
}
