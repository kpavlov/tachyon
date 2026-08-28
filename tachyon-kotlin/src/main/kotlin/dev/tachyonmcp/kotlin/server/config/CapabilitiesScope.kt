// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.api.annotations.ExperimentalApi
import dev.tachyonmcp.api.server.config.Mode
import dev.tachyonmcp.api.server.features.tasks.TaskConnector
import dev.tachyonmcp.core.server.config.CapabilitiesConfig
import dev.tachyonmcp.core.server.config.FeatureConfig
import dev.tachyonmcp.core.server.config.ResourcesConfig
import dev.tachyonmcp.core.server.config.TasksConfig
import dev.tachyonmcp.kotlin.server.TachyonDsl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@TachyonDsl
public class CapabilitiesScope
    @PublishedApi
    internal constructor() {
        @PublishedApi
        internal var toolsConfig: FeatureConfig = FeatureConfig.builder().build()

        @PublishedApi
        internal var resourcesConfig: ResourcesConfig = ResourcesConfig.builder().build()

        @PublishedApi
        internal var promptsConfig: FeatureConfig = FeatureConfig.builder().build()

        @PublishedApi
        internal var tasksConfig: TasksConfig = TasksConfig.builder().build()

        /** Completions capability mode. */
        public var completionsMode: Mode = Mode.AUTO

        /** Whether logging capability is enabled. */
        public var logging: Boolean = false

        @OptIn(ExperimentalContracts::class)
        public inline fun tools(configure: (@TachyonDsl FeatureScope).() -> Unit) {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            toolsConfig = FeatureScope().apply(configure).toConfig()
        }

        @OptIn(ExperimentalContracts::class)
        public inline fun resources(configure: (@TachyonDsl ResourcesScope).() -> Unit) {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            resourcesConfig = ResourcesScope().apply(configure).toConfig()
        }

        @OptIn(ExperimentalContracts::class)
        public inline fun prompts(configure: (@TachyonDsl FeatureScope).() -> Unit) {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            promptsConfig = FeatureScope().apply(configure).toConfig()
        }

        /**
         * Enables task support with the supplied external [TaskConnector].
         *
         * @param connector system that owns task execution
         * @param configure additional task capability configuration
         */
        @OptIn(ExperimentalContracts::class)
        @ExperimentalApi
        public inline fun tasks(
            connector: TaskConnector,
            configure: (@TachyonDsl TasksScope).() -> Unit = {},
        ) {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            tasksConfig = TasksScope(connector).apply(configure).toConfig()
        }

        @PublishedApi
        internal fun applyTo(builder: CapabilitiesConfig.Builder) {
            builder.tools(toolsConfig)
            builder.resources(resourcesConfig)
            builder.prompts(promptsConfig)
            builder.tasks(tasksConfig)
            builder.completions(completionsMode)
            if (logging) builder.logging()
        }
    }
