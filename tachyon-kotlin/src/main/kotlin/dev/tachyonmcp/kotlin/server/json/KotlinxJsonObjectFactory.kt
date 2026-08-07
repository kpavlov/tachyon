// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.json

import dev.tachyonmcp.api.json.JsonDocument
import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.json.spi.JsonDocumentFactory
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory
import kotlinx.serialization.json.JsonObject
import java.util.Optional

/**
 * kotlinx.serialization [JsonObject]-backed [dev.tachyonmcp.api.json.spi.JsonDocumentFactory] and
 * [JsonSchemaFactory]: wraps an already-built object without re-serializing it, retaining it for
 * [dev.tachyonmcp.api.json.JsonDocument.unwrap] instead of round-tripping through a JSON string.
 */
internal class KotlinxJsonObjectFactory :
    JsonDocumentFactory<JsonObject>,
    JsonSchemaFactory {
    companion object {
        @JvmField
        val INSTANCE: KotlinxJsonObjectFactory = KotlinxJsonObjectFactory()

        /** Provider factory used by [java.util.ServiceLoader] to obtain the singleton. */
        @JvmStatic
        fun provider(): KotlinxJsonObjectFactory = INSTANCE
    }

    override fun sourceType(): Class<JsonObject> = JsonObject::class.java

    override fun toJsonDocument(source: JsonObject): JsonDocument =
        KotlinxJsonObjectDocument(source)

    override fun toJsonSchema(
        source: Any,
        type: Class<*>,
    ): Optional<JsonSchema> {
        if (type != JsonObject::class.java) {
            return Optional.empty()
        }
        @Suppress("UNCHECKED_CAST")
        return Optional.of(KotlinxJsonObjectSchema(source as JsonObject))
    }

    fun toJsonSchema(source: JsonObject): JsonSchema = KotlinxJsonObjectSchema(source)
}

private class KotlinxJsonObjectDocument(
    private val obj: JsonObject,
) : JsonDocument {
    override fun json(): String = obj.toString()

    override fun <T : Any> unwrap(type: Class<T>): Optional<T> =
        if (type.isInstance(obj)) Optional.of(type.cast(obj)) else Optional.empty()
}

private class KotlinxJsonObjectSchema(
    private val obj: JsonObject,
) : JsonSchema {
    override fun json(): String = obj.toString()

    override fun <T : Any> unwrap(type: Class<T>): Optional<T> =
        if (type.isInstance(obj)) Optional.of(type.cast(obj)) else Optional.empty()
}
