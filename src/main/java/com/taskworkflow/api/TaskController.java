package com.taskworkflow.api;

import com.taskworkflow.api.dto.CreateTaskRequest;
import com.taskworkflow.api.dto.TaskResponse;
import com.taskworkflow.domain.Task;
import com.taskworkflow.service.TaskService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @GetMapping
    public List<TaskResponse> listTasks() {
        return taskService.listTasks().stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TaskResponse getTask(@PathVariable UUID id) {
        return TaskResponse.from(taskService.getTask(id));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllTasks() {
        taskService.deleteAllTasks();
    }

    @PostMapping("/{id}/pause")
    public TaskResponse pauseTask(@PathVariable UUID id) {
        return TaskResponse.from(taskService.pauseTask(id));
    }

    @PostMapping("/{id}/resume")
    public TaskResponse resumeTask(@PathVariable UUID id) {
        return TaskResponse.from(taskService.resumeTask(id));
    }

    @PostMapping("/{id}/cancel")
    public TaskResponse cancelTask(@PathVariable UUID id) {
        return TaskResponse.from(taskService.cancelTask(id));
    }

    @PostMapping("/reprocess")
    public ReprocessResponse reprocessStuckTasks() {
        return new ReprocessResponse(taskService.reprocessStuckTasks());
    }

    public record ReprocessResponse(int reprocessed) {
    }
}
