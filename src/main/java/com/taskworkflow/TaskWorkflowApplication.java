package com.taskworkflow;

import com.taskworkflow.config.TaskWorkflowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TaskWorkflowProperties.class)
public class TaskWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskWorkflowApplication.class, args);
    }
}
