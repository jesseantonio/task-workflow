package com.taskworkflow.exception;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(UUID id) {
        super("Tarefa não encontrada: " + id);
    }
}
