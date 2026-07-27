// Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
package dev.tachyonmcp.kotlin.server.json

import dev.tachyonmcp.protocol.api.json.JsonDocument
import dev.tachyonmcp.protocol.api.json.JsonSchema
import dev.tachyonmcp.protocol.api.json.spi.JsonDocumentFactory
import dev.tachyonmcp.protocol.api.json.spi.JsonSchemaFactory
import kotlinx.serialization.json.JsonElement
import java.util.Optional

/**
 * kotlinx.serialization [JsonElement]-backed
 * [dev.tachyonmcp.protocol.api.json.spi.JsonDocumentFactory] and [JsonSchemaFactory]: wraps
 * an already-parsed tree without re-serializing it, retaining the element for
 * [dev.tachyonmcp.protocol.api.json.JsonDocument.unwrap] instead of round-tripping through a JSON string.
 */
internal class KotlinxJsonElementFactory :
    JsonDocumentFactory<JsonElement>,
    JsonSchemaFactory<JsonElement> {
    companion object {
        @JvmField
        val INSTANCE: KotlinxJsonElementFactory = KotlinxJsonElementFactory()

        /** Provider factory used by [java.util.ServiceLoader] to obtain the singleton. */
        @JvmStatic
        fun provider(): KotlinxJsonElementFactory = INSTANCE
    }

    override fun sourceType(): Class<JsonElement> = JsonElement::class.java

    override fun toJsonDocument(source: JsonElement): JsonDocument =
        KotlinxJsonElementDocument(source)

    override fun toJsonSchema(source: JsonElement): JsonSchema = KotlinxJsonElementSchema(source)
}

private class KotlinxJsonElementDocument(
    private val element: JsonElement,
) : JsonDocument {
    override fun json(): String = element.toString()

    override fun <T : Any> unwrap(type: Class<T>): Optional<T> =
        if (type.isInstance(element)) Optional.of(type.cast(element)) else Optional.empty()
}

private class KotlinxJsonElementSchema(
    private val element: JsonElement,
) : JsonSchema {
    override fun json(): String = element.toString()

    override fun <T : Any> unwrap(type: Class<T>): Optional<T> =
        if (type.isInstance(element)) Optional.of(type.cast(element)) else Optional.empty()
}
