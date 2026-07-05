@file:Suppress("FunctionName")
@file:JvmName("ToolArgsFactory")

// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.server.json.PayloadDeserializer
import dev.tachyonmcp.server.json.toJacksonNodeMap
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

/**
 * Creates [ToolArgs] wrapping a raw argument map and an optional deserializer.
 *
 * @param raw          raw JSON argument map; null or empty map if no arguments
 * @param deserializer optional deserializer for decoding into typed objects
 */
public fun ToolArgs(
    raw: Map<String, JsonNode>? = null,
    deserializer: PayloadDeserializer? = null,
): ToolArgs = ToolArgs.of(raw, deserializer)

/**
 * Creates [ToolArgs] from a kotlinx-serialization [JsonObject] argument map.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmName("toolArgsWithKxMap")
public fun ToolArgs(
    raw: Map<String, JsonObject>,
    deserializer: PayloadDeserializer? = null,
): ToolArgs = ToolArgs.of(raw.toJacksonNodeMap(), deserializer)
