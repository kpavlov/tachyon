// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
@file:Suppress("ktlint:standard:filename")
// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.

package dev.tachyonmcp.kotlin.server.json.ktschema

import dev.tachyonmcp.api.annotations.ExperimentalApi
import dev.tachyonmcp.api.json.JsonSchema
import kotlinx.serialization.json.Json
import me.kpavlov.kt.schema.generator.json.JsonSchemaConfig
import me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator

/**
 * Builds a `typedTool` `schemaGenerator` lambda backed by kt-schema's
 * [ReflectionClassJsonSchemaGenerator], using [config] instead of the process-wide default's
 * [me.kpavlov.kt.schema.generator.json.JsonSchemaConfig.Strict].
 *
 * For example, `JsonSchemaConfig.Default` lets nullable/defaulted Kotlin properties be omitted
 * instead of required — pass the result as `typedTool(..., schemaGenerator =
 * ktSchemaGenerator(JsonSchemaConfig.Default)) { }`.
 */
@ExperimentalApi
@JvmSynthetic
public fun ktSchemaGenerator(
    config: JsonSchemaConfig = JsonSchemaConfig.Strict,
): (Class<*>) -> JsonSchema {
    val generator =
        ReflectionClassJsonSchemaGenerator(json = Json { encodeDefaults = false }, config = config)
    return { type -> JsonSchema.unchecked(generator.generateSchemaString(type.kotlin)) }
}
