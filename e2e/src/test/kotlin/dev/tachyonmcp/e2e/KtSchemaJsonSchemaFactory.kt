// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.e2e

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory
import me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator

/**
 * Test-only [JsonSchemaFactory] keyed by [Class], registered via `META-INF/services` so the
 * `tachyon-kotlin` reified `typedTool<In, Out>(...)` DSL overload (which calls
 * `JsonSchema.from(Class, Class::class)`) resolves real generated schemas here, backed by
 * kt-schema's [ReflectionClassJsonSchemaGenerator]. `tachyon-kotlin` itself ships no such factory
 * — this is the integration pattern a downstream user would follow to wire one in.
 */
internal class KtSchemaJsonSchemaFactory : JsonSchemaFactory<Class<*>> {
    companion object {
        @JvmField
        val INSTANCE: KtSchemaJsonSchemaFactory = KtSchemaJsonSchemaFactory()

        /** Provider factory used by [java.util.ServiceLoader] to obtain the singleton. */
        @JvmStatic
        fun provider(): KtSchemaJsonSchemaFactory = INSTANCE
    }

    private val generator = ReflectionClassJsonSchemaGenerator.Default

    override fun sourceType(): Class<Class<*>> {
        @Suppress("UNCHECKED_CAST")
        return Class::class.java as Class<Class<*>>
    }

    override fun toJsonSchema(source: Class<*>): JsonSchema =
        JsonSchema.of(generator.generateSchemaString(source.kotlin))
}
