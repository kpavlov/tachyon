// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.ToolArgs
import dev.tachyonmcp.server.features.tools.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

private fun JsonObject.toJacksonNode(): JsonNode = toString().toJsonNode()

/**
 * Registers a tool using a [JsonObject] input schema.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmSynthetic
public fun Server.registerTool(
    name: String,
    description: String? = null,
    inputSchema: JsonObject,
    outputSchema: JsonObject? = null,
    block: suspend ToolScope.() -> ToolResult,
): Server =
    registerTool(
        name = name,
        description = description,
        inputSchema = inputSchema.toJacksonNode(),
        outputSchema = outputSchema?.toJacksonNode(),
        block = block,
    )

/**
 * Registers a tool using a [JsonObject] input schema.
 * Requires kotlinx-serialization-json on the classpath.
 */
public fun ServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: JsonObject,
    outputSchema: JsonObject? = null,
    handler: suspend ToolScope.() -> ToolResult,
): ServerBuilder =
    this.tool(
        name = name,
        description = description,
        inputSchema = inputSchema.toJacksonNode(),
        outputSchema = outputSchema?.toJacksonNode(),
        handler = handler,
    )

/**
 * Registers a tool using a [JsonObject] input schema.
 * Requires kotlinx-serialization-json on the classpath.
 */
public fun TachyonServerBuilder.tool(
    name: String,
    description: String? = null,
    inputSchema: JsonObject,
    outputSchema: JsonObject? = null,
    handler: suspend ToolScope.() -> ToolResult,
): TachyonServerBuilder =
    this.tool(
        name = name,
        description = description,
        inputSchema = inputSchema.toJacksonNode(),
        outputSchema = outputSchema?.toJacksonNode(),
        handler = handler,
    )

/** Sets the input schema from a [JsonObject]. Requires kotlinx-serialization-json on the classpath. */
public fun ToolDescriptorScope.inputSchema(json: JsonObject) {
    inputSchema = json.toJacksonNode()
}

/** Sets the output schema from a [JsonObject]. Requires kotlinx-serialization-json on the classpath. */
public fun ToolDescriptorScope.outputSchema(json: JsonObject) {
    outputSchema = json.toJacksonNode()
}

/**
 * Produces a [ToolResult] with a structured value encoded via kotlinx-serialization.
 * The serialized JSON string serves as the text fallback.
 * The value must encode to a JSON object, as required by the MCP `structuredContent` field.
 * Requires kotlinx-serialization-json on the classpath.
 */
public inline fun <reified T> ToolScope.structured(
    value: T,
    json: Json = Json.Default,
): ToolResult {
    val text = json.encodeToString(value)
    val node: JsonNode = text.toJsonNode()
    require(node.isObject) {
        "structuredContent must be a JSON object, got ${node.nodeType}: $text"
    }
    return ToolResult.of(node, text)
}

@PublishedApi
internal val defaultArgsJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Decodes tool arguments into a [T] using kotlinx-serialization.
 * Unknown keys are ignored by default; pass a custom [json] for strict decoding.
 * Requires kotlinx-serialization-json on the classpath.
 */
public inline fun <reified T> ToolArgs.decode(json: Json = defaultArgsJson): T {
    val text = asMap().let { sharedMapper.writeValueAsString(it) }
    return json.decodeFromString(text)
}
