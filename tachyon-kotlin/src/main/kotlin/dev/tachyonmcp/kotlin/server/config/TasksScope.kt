// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.server.features.tasks.TaskExecutionEngine
import dev.tachyonmcp.core.server.config.TasksConfig
import dev.tachyonmcp.core.server.config.TasksConfig.DEFAULT_TASK_KEEP_ALIVE
import dev.tachyonmcp.core.server.features.Pagination
import dev.tachyonmcp.kotlin.server.TachyonDsl
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

@TachyonDsl
public class TasksScope
    @PublishedApi
    internal constructor() {
        /** Whether the `tasks` capability is advertised at all. */
        public var enabled: Boolean = false

        /** Whether `tasks/list` is exposed. */
        public var list: Boolean = false

        /** Whether `tasks/cancel` is supported. */
        public var cancel: Boolean = false

        /** Whether task-augmented `tools/call` requests are accepted (`tasks.requests.tools.call`). */
        public var requests: Boolean = false

        /** Connector to the system that owns task execution. Required when tasks are enabled. */
        public var executionEngine: TaskExecutionEngine? = null

        /** Default page size when a list request omits its limit. */
        public var pageSize: Int = Pagination.DEFAULT_PAGE_SIZE

        /**
         * How long a completed/failed/cancelled task's result stays retrievable before eviction.
         * Zero or negative disables eviction — the result is kept indefinitely.
         */
        public var keepAlive: Duration = DEFAULT_TASK_KEEP_ALIVE.toKotlinDuration()

        @PublishedApi
        internal fun toConfig(): TasksConfig =
            TasksConfig
                .builder()
                .enabled(enabled)
                .list(list)
                .cancel(cancel)
                .requests(requests)
                .taskExecutionEngine(executionEngine)
                .pageSize(pageSize)
                .keepAlive(keepAlive.toJavaDuration())
                .build()
    }
