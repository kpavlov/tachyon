// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.domain

import dev.tachyonmcp.api.annotations.ExperimentalApi
import dev.tachyonmcp.api.server.features.tools.ToolRequest

/**
 * Decodes this request's arguments into [T] using the deserializer configured in server config.
 * Shorthand for `arguments().decode<T>()`.
 *
 * @throws IllegalStateException if no deserializer is configured for these args.
 */
@JvmSynthetic
@ExperimentalApi
public inline fun <reified T : Any> ToolRequest.arguments(): T = arguments().decode<T>()
