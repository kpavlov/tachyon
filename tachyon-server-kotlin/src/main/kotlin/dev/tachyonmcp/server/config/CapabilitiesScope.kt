// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.TachyonDsl

@TachyonDsl
public class CapabilitiesScope
    @PublishedApi
    internal constructor() {
        /** Tool capability mode: `ON`, `OFF`, or `AUTO`. */
        public var tools: Mode = Mode.AUTO

        /** Whether to advertise tool list change notifications. */
        public var toolsListChanged: Boolean = false

        /** Whether resources capability is enabled. */
        public var resources: Boolean = false

        /** Whether to support resource subscriptions. */
        public var resourcesSubscribe: Boolean = false

        /** Whether to advertise resource list change notifications. */
        public var resourcesListChanged: Boolean = false

        /** Whether prompts capability is enabled. */
        public var prompts: Boolean = false

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

        public fun tools(listChanged: Boolean = false) {
            tools = Mode.ON
            toolsListChanged = listChanged
        }

        public fun resources(
            subscribe: Boolean = false,
            listChanged: Boolean = false,
        ) {
            resources = true
            resourcesSubscribe = subscribe
            resourcesListChanged = listChanged
        }

        public fun prompts(listChanged: Boolean = false) {
            prompts = true
            promptsListChanged = listChanged
        }

        @PublishedApi
        internal fun applyTo(builder: CapabilitiesConfig.Builder) {
            builder.toolsMode(tools)
            if (tools != Mode.OFF) {
                builder.tools(toolsListChanged)
            }
            if (resources) builder.resources(resourcesSubscribe, resourcesListChanged)
            if (prompts) builder.prompts(promptsListChanged)
            if (tasks) builder.tasks(true, tasksCancel, tasksRequests)
            if (completions) builder.completions()
            if (logging) builder.logging()
        }
    }
