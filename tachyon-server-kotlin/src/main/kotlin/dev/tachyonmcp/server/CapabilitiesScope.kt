// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.config.CapabilitiesConfig
import dev.tachyonmcp.server.config.Mode

@TachyonDsl
public class CapabilitiesScope
    @PublishedApi
    internal constructor() {
        public var tools: Mode = Mode.AUTO
        public var toolsListChanged: Boolean = false
        public var resources: Boolean = false
        public var resourcesSubscribe: Boolean = false
        public var resourcesListChanged: Boolean = false
        public var prompts: Boolean = false
        public var promptsListChanged: Boolean = false
        public var tasks: Boolean = false
        public var tasksCancel: Boolean = false
        public var tasksRequests: Boolean = false
        public var completions: Boolean = false
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
