/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.features.Pagination;

/**
 * Configuration for the tasks capability. Fields map 1:1 to the MCP {@code tasks} capability
 * object ({@code tasks.list}, {@code tasks.cancel}, {@code tasks.requests.tools.call}).
 *
 * @param enabled  whether the {@code tasks} capability is advertised at all (default
 *                 {@code false}); the capability is also advertised, regardless of this flag,
 *                 when a registered tool supports task augmentation
 * @param list     whether {@code tasks/list} is exposed (default {@code false})
 * @param cancel   whether {@code tasks/cancel} is supported (default {@code false})
 * @param requests whether task-augmented {@code tools/call} requests are accepted
 *                 (default {@code false})
 * @param pageSize default page size when a list request omits its limit
 */
public record TasksConfig(boolean enabled, boolean list, boolean cancel, boolean requests, int pageSize) {

    static final boolean DEFAULT_TASKS_ENABLED = false;
    static final boolean DEFAULT_TASK_LIST = false;
    static final boolean DEFAULT_TASK_CANCEL = false;
    static final boolean DEFAULT_TASK_REQUESTS = false;

    static final TasksConfig DEFAULT = new TasksConfig(false, false, false, false, Pagination.DEFAULT_PAGE_SIZE);

    public TasksConfig {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
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
        private int pageSize = DEFAULT.pageSize;

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

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /**
         * Enables the tasks capability with the list surface on.
         */
        public Builder on() {
            return enabled(true).list(true);
        }

        public TasksConfig build() {
            return new TasksConfig(enabled, list, cancel, requests, pageSize);
        }
    }
}
