// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.kotlin.server.features.tools

import dev.tachyonmcp.kotlin.server.TachyonDsl
import dev.tachyonmcp.kotlin.server.json.toJsonSchema
import dev.tachyonmcp.server.features.tasks.TaskSupport
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.json.JsonSchema
import dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema
import kotlinx.serialization.json.JsonObject
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
        public var inputSchema: JsonSchema? = null
        public var outputSchema: JsonSchema? = null
        public var taskSupport: TaskSupport? = null
        public var annotations: dev.tachyonmcp.server.domain.ToolAnnotations? = null
        public var icons: List<dev.tachyonmcp.server.domain.Icon>? = null
        public var extensionId: String? = null

        public fun inputSchema(json: String) {
            inputSchema = parseSchema(json)
        }

        public fun outputSchema(json: String) {
            outputSchema = parseSchema(json)
        }

        /** Sets the input schema from a kotlinx-serialization [JsonObject]. */
        public fun inputSchema(json: JsonObject) {
            inputSchema = json.toJsonSchema()
        }

        /** Sets the output schema from a kotlinx-serialization [JsonObject]. */
        public fun outputSchema(json: JsonObject) {
            outputSchema = json.toJsonSchema()
        }

        @PublishedApi
        internal fun build(): ToolDescriptor {
            val n = requireNotNull(name) { "ToolDescriptor.name is required" }
            val builder =
                ToolDescriptor
                    .builder()
                    .name(
                        n,
                    )
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
): dev.tachyonmcp.server.features.tools.ToolDescriptor {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }
    return ToolDescriptorScope()
        .apply {
            this.name = name
            configure()
        }.build()
}

/** Builds a [ToolDescriptor] with a receiver DSL. */
@OptIn(ExperimentalContracts::class)
public inline fun ToolDescriptor(
    block: ToolDescriptorScope.() -> Unit,
): dev.tachyonmcp.server.features.tools.ToolDescriptor {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ToolDescriptorScope().apply(block).build()
}
