// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.features.tools.ToolArgs

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
