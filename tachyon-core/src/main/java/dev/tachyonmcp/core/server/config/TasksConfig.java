/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.config;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.features.tasks.TaskConnector;
import dev.tachyonmcp.core.server.features.Pagination;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the tasks capability.
 *
 * @param enabled      whether the {@code tasks} capability is advertised at all (default
 *                     {@code false}); the capability is also advertised, regardless of this flag,
 *                     when a registered tool supports task augmentation
 * @param connector    connector to the system that owns task execution; modern operations are
 *                     required, while legacy list/result support is read from the connector
 * @param pageSize     default page size when a list request omits its limit
 * @param keepAlive    default retention window for a terminal task's cached result (default 5
 *                     minutes)
 * @param pollInterval default {@code pollInterval} suggested to requestors in task responses, or
 *                     {@code null} (the default) to suggest none when a snapshot omits one
 */
@ExperimentalApi
public record TasksConfig(
        boolean enabled,
        @Nullable TaskConnector connector,
        int pageSize,
        Duration keepAlive,
        @Nullable Duration pollInterval) {

    static final boolean DEFAULT_TASKS_ENABLED = false;

    /** Default retention window for terminal snapshots in the internal cache. */
    public static final Duration DEFAULT_TASK_KEEP_ALIVE = Duration.ofMinutes(5);

    static final TasksConfig DEFAULT =
            new TasksConfig(false, null, Pagination.DEFAULT_PAGE_SIZE, DEFAULT_TASK_KEEP_ALIVE, null);

    public TasksConfig {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        Objects.requireNonNull(keepAlive, "keepAlive");
        if (pollInterval != null && (pollInterval.isZero() || pollInterval.isNegative())) {
            throw new IllegalArgumentException("pollInterval must be positive, got: " + pollInterval);
        }
    }

    /** Whether the connector supports the legacy (pre-SEP-2663) {@code tasks/list} operation. */
    @SuppressWarnings("deprecation")
    public boolean list() {
        return connector != null && connector.list() != null;
    }

    /** Whether the connector supports {@code tasks/cancel}. */
    public boolean cancel() {
        return connector != null;
    }

    /** Whether the connector supports {@code tasks/update}. */
    public boolean requests() {
        return connector != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TasksConfig}.
     */
    @ExperimentalApi
    public static final class Builder {

        private boolean enabled = DEFAULT_TASKS_ENABLED;
        private @Nullable TaskConnector connector = DEFAULT.connector;
        private int pageSize = DEFAULT.pageSize;
        private Duration keepAlive = DEFAULT.keepAlive;
        private @Nullable Duration pollInterval = DEFAULT.pollInterval;

        private Builder() {}

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /** Sets the connector to the system that owns task execution. */
        public Builder connector(@Nullable TaskConnector connector) {
            this.connector = connector;
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

        public TasksConfig build() {
            return new TasksConfig(enabled, connector, pageSize, keepAlive, pollInterval);
        }
    }
}
