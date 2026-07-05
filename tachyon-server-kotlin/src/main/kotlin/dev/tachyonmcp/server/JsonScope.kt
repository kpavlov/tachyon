// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import dev.tachyonmcp.server.json.JsonSchemaValidator
import dev.tachyonmcp.server.json.PayloadSerde

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
         * Defaults to Jackson; assign [KxPayloadSerde] for kotlinx.serialization.
         */
        public var serde: PayloadSerde? = null

        /** Input schema validator, or `null` to keep the server default. */
        public var inputValidator: JsonSchemaValidator? = null

        /** Output schema validator, or `null` to keep the server default. */
        public var outputValidator: JsonSchemaValidator? = null

        @PublishedApi
        internal fun applyTo(builder: ServerBuilder) {
            serde?.let { builder.payloadSerde(it) }
            inputValidator?.let { builder.inputSchemaValidator(it) }
            outputValidator?.let { builder.outputSchemaValidator(it) }
        }
    }
