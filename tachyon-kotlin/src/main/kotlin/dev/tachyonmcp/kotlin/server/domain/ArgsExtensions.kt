/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.kotlin.server.domain

public fun dev.tachyonmcp.server.domain.Args.stringOrNull(key: String): String? =
    stringOpt(key).orElse(null)

public fun dev.tachyonmcp.server.domain.Args.intOrNull(key: String): Int? =
    intOpt(key).let { if (it.isPresent) it.asInt else null }

public fun dev.tachyonmcp.server.domain.Args.booleanOrNull(key: String): Boolean? =
    boolOpt(key).orElse(null)

public fun dev.tachyonmcp.server.domain.Args.doubleOrNull(key: String): Double? =
    doubleOpt(key).let { if (it.isPresent) it.asDouble else null }

public fun dev.tachyonmcp.server.domain.Args.boolean(
    key: String,
    default: Boolean,
): Boolean = boolOr(key, default)

public fun dev.tachyonmcp.server.domain.Args.int(
    key: String,
    default: Int,
): Int = intOr(key, default)

public fun dev.tachyonmcp.server.domain.Args.double(
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
public inline fun <reified T : Any> dev.tachyonmcp.server.domain.Args.decode(): T =
    decode(T::class.java)
