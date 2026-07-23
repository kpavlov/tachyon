// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.ToolAnnotations
import dev.tachyonmcp.server.features.tasks.TaskSupport
import dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema
import dev.tachyonmcp.server.json.toJacksonNode
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@TachyonDsl
public class ToolDescriptorScope
    @PublishedApi
    internal constructor() {
        public var name: String? = null
        public var title: String? = null
        public var description: String? = null
        public var inputSchema: JsonNode? = null
        public var outputSchema: JsonNode? = null
        public var taskSupport: TaskSupport? = null
        public var annotations: ToolAnnotations? = null
        public var icons: List<Icon>? = null
        public var extensionId: String? = null

        public fun inputSchema(json: String) {
            inputSchema = parseSchema(json)
        }

        public fun outputSchema(json: String) {
            outputSchema = parseSchema(json)
        }

        /** Sets the input schema from a kotlinx-serialization [JsonObject]. */
        public fun inputSchema(json: JsonObject) {
            inputSchema = json.toJacksonNode()
        }

        /** Sets the output schema from a kotlinx-serialization [JsonObject]. */
        public fun outputSchema(json: JsonObject) {
            outputSchema = json.toJacksonNode()
        }

        @PublishedApi
        internal fun build(): ToolDescriptor {
            val n = requireNotNull(name) { "ToolDescriptor.name is required" }
            val builder = ToolDescriptor.builder().name(n)
            title?.let(builder::title)
            description?.let(builder::description)
            inputSchema?.let(builder::inputSchema)
            outputSchema?.let(builder::outputSchema)
            taskSupport?.let(builder::taskSupport)
            annotations?.let(builder::annotations)
            icons?.let(builder::icons)
            extensionId?.let(builder::extensionId)
            return builder.build()
        }
    }

@OptIn(ExperimentalContracts::class)
public inline fun toolDescriptor(
    name: String,
    configure: ToolDescriptorScope.() -> Unit = {},
): ToolDescriptor {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return ToolDescriptorScope()
        .apply {
            this.name = name
            configure()
        }.build()
}

/** Builds a [ToolDescriptor] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun ToolDescriptor(block: ToolDescriptorScope.() -> Unit): ToolDescriptor {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ToolDescriptorScope().apply(block).build()
}
