// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.features.Pagination

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

        @PublishedApi
        internal fun toConfig(): TasksConfig =
            TasksConfig
                .builder()
                .enabled(enabled)
                .list(list)
                .cancel(cancel)
                .requests(requests)
                .pageSize(pageSize)
                .build()
    }
