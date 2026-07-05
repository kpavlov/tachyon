// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.domain.ToolAnnotations
import dev.tachyonmcp.server.features.tasks.TaskSupport
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.json.JsonSchemaUtils.parseSchema
import tools.jackson.databind.JsonNode

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

        public fun inputSchema(json: String) {
            inputSchema = parseSchema(json, name ?: "<unknown>")
        }

        public fun outputSchema(json: String) {
            outputSchema = parseSchema(json, name ?: "<unknown>")
        }

        @PublishedApi
        internal fun build(): ToolDescriptor {
            val n = requireNotNull(name) { "ToolDescriptor.name is required" }
            val builder = ToolDescriptor.builder(n)
            title?.let(builder::title)
            description?.let(builder::description)
            inputSchema?.let(builder::inputSchema)
            outputSchema?.let(builder::outputSchema)
            taskSupport?.let(builder::taskSupport)
            annotations?.let(builder::annotations)
            return builder.build()
        }
    }

public fun toolDescriptor(
    name: String,
    configure: ToolDescriptorScope.() -> Unit = {},
): ToolDescriptor =
    ToolDescriptorScope()
        .apply {
            this.name = name
            configure()
        }.build()
