// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.features.Pagination

@TachyonDsl
public class CapabilitiesScope
    @PublishedApi
    internal constructor() {
        /** Tool capability mode: `ON`, `OFF`, or `AUTO`. */
        public var tools: Mode = Mode.AUTO

        /** Whether to advertise tool list change notifications. */
        public var toolsListChanged: Boolean = false

        /** Resources capability mode: `ON`, `OFF`, or `AUTO`. */
        public var resources: Mode = Mode.AUTO

        /** Whether to support resource subscriptions. */
        public var resourcesSubscribe: Boolean = false

        /** Whether to advertise resource list change notifications. */
        public var resourcesListChanged: Boolean = false

        /** Prompts capability mode: `ON`, `OFF`, or `AUTO`. */
        public var prompts: Mode = Mode.AUTO

        /** Whether to advertise prompt list change notifications. */
        public var promptsListChanged: Boolean = false

        /** Whether tasks capability is enabled. */
        public var tasks: Boolean = false

        /** Whether to support task cancellation. */
        public var tasksCancel: Boolean = false

        /** Whether to support task requests. */
        public var tasksRequests: Boolean = false

        /** Whether completions capability is enabled. */
        public var completions: Boolean = false

        /** Whether logging capability is enabled. */
        public var logging: Boolean = false

        /** Default page size for tools/list when limit is omitted. */
        public var toolsPageSize: Int = Pagination.DEFAULT_PAGE_SIZE

        /** Default page size for resources/list when limit is omitted. */
        public var resourcesPageSize: Int = Pagination.DEFAULT_PAGE_SIZE

        /** Default page size for prompts/list when limit is omitted. */
        public var promptsPageSize: Int = Pagination.DEFAULT_PAGE_SIZE

        /** Default page size for tasks/list when limit is omitted. */
        public var tasksPageSize: Int = Pagination.DEFAULT_PAGE_SIZE

        public fun tools(listChanged: Boolean = false) {
            tools = Mode.ON
            toolsListChanged = listChanged
        }

        public fun resources(
            subscribe: Boolean = false,
            listChanged: Boolean = false,
        ) {
            resources = Mode.ON
            resourcesSubscribe = subscribe
            resourcesListChanged = listChanged
        }

        public fun prompts(listChanged: Boolean = false) {
            prompts = Mode.ON
            promptsListChanged = listChanged
        }

        @PublishedApi
        internal fun applyTo(builder: CapabilitiesConfig.Builder) {
            builder.toolsMode(tools)
            builder.toolsListChanged(toolsListChanged)
            builder.resourcesMode(resources)
            builder.resourcesSubscribe(resourcesSubscribe)
            builder.resourcesListChanged(resourcesListChanged)
            builder.promptsMode(prompts)
            builder.promptsListChanged(promptsListChanged)
            if (tasks) builder.tasks(true, tasksCancel, tasksRequests)
            if (completions) builder.completions()
            if (logging) builder.logging()
            builder.toolsPageSize(toolsPageSize)
            builder.resourcesPageSize(resourcesPageSize)
            builder.promptsPageSize(promptsPageSize)
            builder.tasksPageSize(tasksPageSize)
        }
    }
