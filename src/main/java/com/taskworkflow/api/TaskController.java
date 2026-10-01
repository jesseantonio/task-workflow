package com.taskworkflow.api;

import com.taskworkflow.api.dto.CreateTaskRequest;
import com.taskworkflow.api.dto.TaskResponse;
import com.taskworkflow.domain.Task;
import com.taskworkflow.service.TaskService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.createTask(request.name(), request.repositoryPath(), request.testCommand());
        TaskResponse response = TaskResponse.from(task);
        return ResponseEntity.created(URI.create("/tasks/" + task.getId())).body(response);
    }

    @GetMapping("/{id}")
    public TaskResponse getTask(@PathVariable UUID id) {
        return TaskResponse.from(taskService.getTask(id));
    }
}
