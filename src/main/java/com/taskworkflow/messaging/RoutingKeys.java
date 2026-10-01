package com.taskworkflow.messaging;

public final class RoutingKeys {

    public static final String TASK_CREATED = "task.created";
    public static final String DEVELOPMENT_COMPLETED = "development.completed";
    public static final String REVIEW_APPROVED = "review.approved";
    public static final String REVIEW_REJECTED = "review.rejected";
    public static final String TEST_SUCCEEDED = "test.succeeded";
    public static final String TEST_FAILED = "test.failed";
    public static final String TASK_RETRY = "task.retry";
    public static final String TASK_DEAD = "task.dead";

    private RoutingKeys() {
    }
}
