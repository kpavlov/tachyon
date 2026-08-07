// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.json

import dev.tachyonmcp.api.annotations.ExperimentalApi
import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory
import me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator

/**
 * [JsonSchemaFactory] keyed by [Class], registered via `META-INF/services` so the reified
 * `typedTool<In, Out>(...)` DSL overload (which calls `JsonSchema.from(Class, Class::class)`)
 * resolves real generated schemas here, backed by kt-schema's
 * [ReflectionClassJsonSchemaGenerator].
 *
 * Ships in the dedicated `tachyon-kotlin-kt-schema` integration artifact, which declares
 * `kt-schema-generator-json-jvm` as a regular (non-optional) dependency. The provider therefore
 * always loads once that artifact is on the classpath — add it explicitly to use `typedTool`.
 */
@ExperimentalApi
internal class KtSchemaJsonSchemaFactory : JsonSchemaFactory<Class<*>> {
    private val generator = ReflectionClassJsonSchemaGenerator.Default

    override fun sourceType(): Class<Class<*>> {
        @Suppress("UNCHECKED_CAST")
        return Class::class.java
    }

    override fun toJsonSchema(source: Class<*>): JsonSchema =
        JsonSchema.of(generator.generateSchemaString(source.kotlin))
}
