package com.taskworkflow.messaging;

public final class Queues {

    public static final String DEV = "dev.queue";
    public static final String REVIEW = "review.queue";
    public static final String TEST = "test.queue";
    public static final String RETRY = "retry.queue";
    public static final String COMPLETED = "completed.queue";
    public static final String DLQ = "task.dlq";

    private Queues() {
    }
}
