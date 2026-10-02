package com.taskworkflow.exception;

import com.taskworkflow.domain.TaskStatus;
import java.util.UUID;

public class InvalidTaskStateException extends RuntimeException {

    public InvalidTaskStateException(UUID id, TaskStatus current, String action) {
        super("Não é possível " + action + " a tarefa " + id + " no estado atual (" + current + ")");
    }
}
