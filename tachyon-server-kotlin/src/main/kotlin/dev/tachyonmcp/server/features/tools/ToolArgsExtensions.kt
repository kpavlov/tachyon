// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.features.tools

import dev.tachyonmcp.server.json.KxSerializationSerde
import kotlinx.serialization.json.Json

public fun ToolArgs.stringOrNull(key: String): String? = if (has(key)) string(key) else null

public fun ToolArgs.intOrNull(key: String): Int? = if (has(key)) intValue(key) else null

public fun ToolArgs.booleanOrNull(key: String): Boolean? = if (has(key)) boolValue(key) else null

public fun ToolArgs.doubleOrNull(key: String): Double? = if (has(key)) doubleValue(key) else null

public fun ToolArgs.boolean(
    key: String,
    default: Boolean,
): Boolean = boolOr(key, default)

public fun ToolArgs.int(
    key: String,
    default: Int,
): Int = intOr(key, default)

public fun ToolArgs.double(
    key: String,
    default: Double,
): Double = doubleOr(key, default)

/**
 * Decodes tool arguments into a [T] using kotlinx-serialization.
 * Uses [ToolArgs.rawJson] to avoid a Jackson round-trip.
 * Unknown keys are ignored by default; pass a custom [json] for strict decoding.
 * Requires kotlinx-serialization-json on the classpath.
 */
public inline fun <reified T> ToolArgs.decode(json: Json = KxSerializationSerde.json): T =
    json.decodeFromString(rawJson())
