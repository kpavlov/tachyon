/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import java.util.Map;
import java.util.Set;

/**
 * See A2A <a href="https://a2a-protocol.org/latest/specification/#413-taskstate">Task State</a>
 */
public enum TaskState {
    SUBMITTED,
    REJECTED,
    /**
     * Indicates that authentication is required to proceed. This is an interrupted state.
     */
    AUTH_REQUIRED,
    WORKING,
    INPUT_REQUIRED,
    COMPLETED,
    FAILED,
    CANCELLED,
    TASK_STATE_UNSPECIFIED;

    private static final Map<TaskState, Set<TaskState>> TRANSITIONS = Map.of(
            SUBMITTED, Set.of(REJECTED, AUTH_REQUIRED, CANCELLED, WORKING),
            AUTH_REQUIRED, Set.of(REJECTED, SUBMITTED, CANCELLED),
            WORKING, Set.of(INPUT_REQUIRED, COMPLETED, FAILED, CANCELLED),
            INPUT_REQUIRED, Set.of(WORKING, COMPLETED, FAILED, CANCELLED),
            COMPLETED, Set.of(),
            FAILED, Set.of(),
            CANCELLED, Set.of());

    public boolean canTransitionTo(TaskState target) {
        var allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == WORKING || this == INPUT_REQUIRED;
    }
}
