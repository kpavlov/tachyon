// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.json

import dev.tachyonmcp.api.annotations.ExperimentalApi
import dev.tachyonmcp.api.json.JsonSchema

@PublishedApi
@JvmSynthetic
@ExperimentalApi
internal inline fun <reified T : Any> generatedJsonSchema(): JsonSchema {
    @Suppress("UNCHECKED_CAST")
    val classToken = Class::class.java as Class<Class<T>>
    return JsonSchema.from(T::class.java, classToken)
}
