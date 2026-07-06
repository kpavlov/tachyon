// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.features.tools

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
 * Decodes tool arguments into [T] using the deserializer configured in server config
 * (kotlinx-serialization by default in the Kotlin DSL, or a custom serde). Honors a
 * custom-configured `Json` — configure it via `json { serde = KxSerializationSerde(Json { … }) }`.
 * Generic type arguments of containers are erased at runtime — prefer dedicated
 * [@Serializable][kotlinx.serialization.Serializable] payload classes.
 *
 * @throws IllegalStateException if no deserializer is configured for these args.
 */
public inline fun <reified T : Any> ToolArgs.decode(): T = decode(T::class.java)
