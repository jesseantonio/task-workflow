package com.taskworkflow.api.dto;

import com.taskworkflow.domain.Task;
import com.taskworkflow.domain.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String correlationId,
        String name,
        TaskStatus status,
        int attempts,
        Instant createdAt,
        Instant updatedAt,
        String developmentOutput,
        String reviewFeedback,
        String testReport,
        String repositoryPath,
        String branchName) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getCorrelationId(),
                task.getName(),
                task.getStatus(),
                task.getAttempts(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getDevelopmentOutput(),
                task.getReviewFeedback(),
                task.getTestReport(),
                task.getRepositoryPath(),
                task.getBranchName());
    }
}
