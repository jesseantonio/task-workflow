package com.taskworkflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false, unique = true)
    private String correlationId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Preenchidos apenas quando task-workflow.ai.enabled=true (ver ClaudeCodeClient). */
    @Column(columnDefinition = "TEXT")
    private String developmentOutput;

    @Column(columnDefinition = "TEXT")
    private String reviewFeedback;

    @Column(columnDefinition = "TEXT")
    private String testReport;

    /** Preenchidos apenas em modo repositório real (ver GitService). */
    @Column(length = 1024)
    private String repositoryPath;

    @Column(length = 255)
    private String branchName;

    @Column(length = 1024)
    private String worktreePath;

    @Column(length = 500)
    private String testCommand;

    public Task(String name) {
        this.id = UUID.randomUUID();
        this.correlationId = this.id.toString();
        this.name = name;
        this.status = TaskStatus.CREATED;
        this.attempts = 0;
    }

    /** Só aplica a transição se o estado atual estiver entre os informados. */
    public boolean transitionTo(TaskStatus target, TaskStatus... allowedCurrent) {
        for (TaskStatus current : allowedCurrent) {
            if (this.status == current) {
                this.status = target;
                return true;
            }
        }
        return false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
