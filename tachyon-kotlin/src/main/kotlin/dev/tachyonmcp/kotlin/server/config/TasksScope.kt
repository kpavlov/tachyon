// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.server.config.TasksConfig
import dev.tachyonmcp.server.config.TasksConfig.DEFAULT_TASK_KEEP_ALIVE
import dev.tachyonmcp.server.features.Pagination
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
                .pageSize(pageSize)
                .keepAlive(keepAlive.toJavaDuration())
                .build()
    }
