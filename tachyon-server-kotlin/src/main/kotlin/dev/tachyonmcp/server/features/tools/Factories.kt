@file:Suppress("FunctionName")
@file:JvmName("ArgsFactory")

// Copyright (c) 2026 Konstantin Pavlov and contributors.

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.server.domain.Args
import dev.tachyonmcp.server.json.PayloadDeserializer
import dev.tachyonmcp.server.json.toJacksonNodeMap
import kotlinx.serialization.json.JsonObject
import tools.jackson.databind.JsonNode

/**
 * Creates [Args] wrapping a raw argument map and an optional deserializer.
 *
 * @param raw          raw JSON argument map; null or empty map if no arguments
 * @param deserializer optional deserializer for decoding into typed objects
 */
public fun Args(
    raw: Map<String, JsonNode>? = null,
    deserializer: PayloadDeserializer? = null,
): Args = Args.of(raw, deserializer)

/**
 * Creates [Args] from a kotlinx-serialization [JsonObject] argument map.
 * Requires kotlinx-serialization-json on the classpath.
 */
@JvmName("toolArgsWithKxMap")
public fun Args(
    raw: Map<String, JsonObject>,
    deserializer: PayloadDeserializer? = null,
): Args = Args.of(raw.toJacksonNodeMap(), deserializer)
