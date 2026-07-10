// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.TachyonDsl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@TachyonDsl
public class CapabilitiesScope
    @PublishedApi
    internal constructor() {
        @PublishedApi
        internal var toolsConfig: FeatureConfig = FeatureConfig.DEFAULT

        @PublishedApi
        internal var resourcesConfig: ResourcesConfig = ResourcesConfig.DEFAULT

        @PublishedApi
        internal var promptsConfig: FeatureConfig = FeatureConfig.DEFAULT

        @PublishedApi
        internal var tasksConfig: TasksConfig = TasksConfig.DEFAULT

        /** Whether completions capability is enabled. */
        public var completions: Boolean = false

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
            if (completions) builder.completions()
            if (logging) builder.logging()
        }
    }
