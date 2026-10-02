package com.taskworkflow.domain;

/**
 * Estados da máquina de estados da Task (ver README, seção 2.5):
 *
 * <pre>
 * CREATED -&gt; IN_DEV -&gt; IN_REVIEW -&gt; IN_TEST -&gt; COMPLETED
 *               ^                       |
 *               |                       v
 *               +------- RETRYING &lt;-----+
 *                          |
 *                          v (attempts >= maxAttempts)
 *                         DEAD
 * </pre>
 *
 * PAUSED e CANCELLED são estados administrativos (ver TaskService).
 */
public enum TaskStatus {
    CREATED,
    IN_DEV,
    IN_REVIEW,
    IN_TEST,
    RETRYING,
    COMPLETED,
    DEAD,
    PAUSED,
    CANCELLED
}
