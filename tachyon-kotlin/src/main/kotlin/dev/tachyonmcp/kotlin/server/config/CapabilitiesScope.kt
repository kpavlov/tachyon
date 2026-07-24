// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.config

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.server.config.CapabilitiesConfig
import dev.tachyonmcp.server.config.FeatureConfig
import dev.tachyonmcp.server.config.Mode
import dev.tachyonmcp.server.config.ResourcesConfig
import dev.tachyonmcp.server.config.TasksConfig
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

        @OptIn(ExperimentalContracts::class)
        public inline fun tasks(configure: (@TachyonDsl TasksScope).() -> Unit) {
            contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
            tasksConfig = TasksScope().apply(configure).toConfig()
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
