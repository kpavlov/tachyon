/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.tasks;

import java.util.Map;
import java.util.Set;

/**
 * Task lifecycle states — superset of MCP 2025-11-25 + A2A.
 *
 * <p>Wire mapping (only the MCP-5 states are visible on the wire):
 * <pre>
 *   SUBMITTED      → "working"      (internal pre-state, never exposed)
 *   REJECTED       → "failed"
 *   AUTH_REQUIRED  → "failed"       (interrupted, mapped as failure)
 *   WORKING        → "working"
 *   INPUT_REQUIRED → "input-required"
 *   COMPLETED      → "completed"
 *   FAILED         → "failed"
 *   CANCELLED      → "cancelled"
 *   UNKNOWN        → error          (not sent on wire)
 * </pre>
 *
 * <p>See <a href="https://modelcontextprotocol.io/seps/1686-tasks">SEP-1686</a> and
 * A2A <a href="https://a2a-protocol.org/latest/specification/#413-taskstate">Task State</a>
 */
public enum TaskState {
    SUBMITTED(false),
    REJECTED(true),
    /**
     * Indicates that authentication is required to proceed. This is an interrupted state.
     */
    AUTH_REQUIRED(false),
    WORKING(false),
    INPUT_REQUIRED(false),
    COMPLETED(true),
    FAILED(true),
    CANCELLED(true),
    UNKNOWN(true);
    private final boolean terminal;

    TaskState(boolean terminal) {
        this.terminal = terminal;
    }

    private static final Map<TaskState, Set<TaskState>> TRANSITIONS = Map.of(
            SUBMITTED, Set.of(REJECTED, AUTH_REQUIRED, CANCELLED, FAILED, COMPLETED, WORKING, UNKNOWN),
            AUTH_REQUIRED, Set.of(REJECTED, SUBMITTED, CANCELLED, UNKNOWN),
            WORKING, Set.of(INPUT_REQUIRED, COMPLETED, FAILED, CANCELLED, UNKNOWN),
            INPUT_REQUIRED, Set.of(WORKING, COMPLETED, FAILED, CANCELLED, UNKNOWN),
            COMPLETED, Set.of(),
            FAILED, Set.of(),
            CANCELLED, Set.of(),
            UNKNOWN, Set.of());

    public boolean canTransitionTo(TaskState target) {
        var allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isActive() {
        return this == WORKING || this == INPUT_REQUIRED;
    }
}
