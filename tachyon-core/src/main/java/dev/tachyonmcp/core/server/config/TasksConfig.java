/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.config;

import dev.tachyonmcp.api.server.features.tasks.TaskExecutionEngine;
import dev.tachyonmcp.core.server.features.Pagination;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the tasks capability. Fields map 1:1 to the MCP {@code tasks} capability
 * object ({@code tasks.list}, {@code tasks.cancel}, {@code tasks.requests.tools.call}).
 *
 * @param enabled      whether the {@code tasks} capability is advertised at all (default
 *                     {@code false}); the capability is also advertised, regardless of this flag,
 *                     when a registered tool supports task augmentation
 * @param list         whether {@code tasks/list} is exposed (default {@code false})
 * @param cancel       whether {@code tasks/cancel} is supported (default {@code false})
 * @param requests     whether task-augmented {@code tools/call} requests are accepted
 *                     (default {@code false})
 * @param taskExecutionEngine connector to the system that owns task execution
 * @param pageSize     default page size when a list request omits its limit
 * @param keepAlive    default retention window for a terminal task's cached result (default 5
 *                     minutes)
 * @param pollInterval default {@code pollInterval} suggested to requestors in task responses, or
 *                     {@code null} (the default) to suggest none when a snapshot omits one
 */
public record TasksConfig(
        boolean enabled,
        boolean list,
        boolean cancel,
        boolean requests,
        @Nullable TaskExecutionEngine taskExecutionEngine,
        int pageSize,
        Duration keepAlive,
        @Nullable Duration pollInterval) {

    static final boolean DEFAULT_TASKS_ENABLED = false;
    static final boolean DEFAULT_TASK_LIST = false;
    static final boolean DEFAULT_TASK_CANCEL = false;
    static final boolean DEFAULT_TASK_REQUESTS = false;

    /** Default retention window for terminal snapshots in the internal cache. */
    public static final Duration DEFAULT_TASK_KEEP_ALIVE = Duration.ofMinutes(5);

    static final TasksConfig DEFAULT = new TasksConfig(
            false, false, false, false, null, Pagination.DEFAULT_PAGE_SIZE, DEFAULT_TASK_KEEP_ALIVE, null);

    public TasksConfig {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        Objects.requireNonNull(keepAlive, "keepAlive");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TasksConfig}.
     */
    public static final class Builder {

        private boolean enabled = DEFAULT_TASKS_ENABLED;
        private boolean list = DEFAULT.list;
        private boolean cancel = DEFAULT.cancel;
        private boolean requests = DEFAULT.requests;
        private @Nullable TaskExecutionEngine taskExecutionEngine = DEFAULT.taskExecutionEngine;
        private int pageSize = DEFAULT.pageSize;
        private Duration keepAlive = DEFAULT.keepAlive;
        private @Nullable Duration pollInterval = DEFAULT.pollInterval;

        private Builder() {}

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder list(boolean list) {
            this.list = list;
            return this;
        }

        public Builder cancel(boolean cancel) {
            this.cancel = cancel;
            return this;
        }

        public Builder requests(boolean requests) {
            this.requests = requests;
            return this;
        }

        /** Sets the engine that owns or connects to task execution. */
        public Builder taskExecutionEngine(@Nullable TaskExecutionEngine taskExecutionEngine) {
            this.taskExecutionEngine = taskExecutionEngine;
            return this;
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /** Sets the default retention window for a terminal task's result. Default is 5 minutes. */
        public Builder keepAlive(Duration keepAlive) {
            this.keepAlive = Objects.requireNonNull(keepAlive, "keepAlive cannot be null");
            return this;
        }

        /**
         * Sets the default {@code pollInterval} suggested in task responses. Default is {@code null}
         * (suggest none); pass a value only if it fits how long tasks on this server actually run —
         * spec-compliant requestors throttle their own polling to match it.
         */
        public Builder pollInterval(@Nullable Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /**
         * Enables the tasks capability with the list surface on.
         */
        public Builder on() {
            return enabled(true).list(true);
        }

        public TasksConfig build() {
            return new TasksConfig(
                    enabled, list, cancel, requests, taskExecutionEngine, pageSize, keepAlive, pollInterval);
        }
    }
}
