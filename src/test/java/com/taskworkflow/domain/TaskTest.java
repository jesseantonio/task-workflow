package com.taskworkflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    void createsTaskInCreatedStatusWithAttemptsZero() {
        Task task = new Task("minha-tarefa");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(task.getAttempts()).isZero();
        assertThat(task.getCorrelationId()).isEqualTo(task.getId().toString());
    }

    @Test
    void appliesTransitionWhenCurrentStatusIsAllowed() {
        Task task = new Task("minha-tarefa");

        boolean applied = task.transitionTo(TaskStatus.IN_DEV, TaskStatus.CREATED, TaskStatus.IN_DEV);

        assertThat(applied).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_DEV);
    }

    @Test
    void rejectsTransitionWhenCurrentStatusIsNotAllowed() {
        Task task = new Task("minha-tarefa");
        task.setStatus(TaskStatus.COMPLETED);

        boolean applied = task.transitionTo(TaskStatus.IN_DEV, TaskStatus.CREATED, TaskStatus.RETRYING);

        assertThat(applied).isFalse();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }
}
