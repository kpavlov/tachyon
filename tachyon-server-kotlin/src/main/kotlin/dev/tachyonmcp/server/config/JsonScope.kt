// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server.config

import dev.tachyonmcp.server.ServerBuilder
import dev.tachyonmcp.server.TachyonDsl
import dev.tachyonmcp.server.json.JsonSchemaValidator
import dev.tachyonmcp.server.json.KxSerializationSerde
import dev.tachyonmcp.server.json.PayloadDeserializer
import dev.tachyonmcp.server.json.PayloadSerde
import dev.tachyonmcp.server.json.PayloadSerializer

/**
 * Configures the JSON payload boundary: payload serde and input/output schema validators.
 * Mirrors the `dev.tachyonmcp.server.json` package.
 *
 * Assign [JsonSchemaValidator.NOOP] to skip validation for a direction.
 *
 * @author Konstantin Pavlov
 */
@TachyonDsl
public class JsonScope
    @PublishedApi
    internal constructor() {
        /**
         * Payload serializer/deserializer for structured values and arguments.
         * Defaults to [KxSerializationSerde] for the Kotlin DSL; set to `null` to restore the Jackson default.
         * Sets both [serializer] and [deserializer] when assigned.
         */
        public var serde: PayloadSerde? = KxSerializationSerde.Default

        /** Payload serializer, or `null` to keep the server default. */
        public var serializer: PayloadSerializer? = null

        /** Payload deserializer, or `null` to keep the server default. */
        public var deserializer: PayloadDeserializer? = null

        /** Input schema validator, or `null` to keep the server default. */
        public var inputValidator: JsonSchemaValidator? = null

        /** Output schema validator, or `null` to keep the server default. */
        public var outputValidator: JsonSchemaValidator? = null

        @PublishedApi
        internal fun applyTo(builder: ServerBuilder) {
            builder.json { config ->
                serde?.let { config.serde(it) }
                serializer?.let { config.serializer(it) }
                deserializer?.let { config.deserializer(it) }
                inputValidator?.let { config.inputSchemaValidator(it) }
                outputValidator?.let { config.outputSchemaValidator(it) }
            }
        }
    }
