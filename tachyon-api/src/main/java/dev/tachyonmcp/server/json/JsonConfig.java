/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import org.jspecify.annotations.Nullable;

/**
 * JSON payload configuration for the server: serializer, deserializer, schema validators, and
 * document/schema parsing factories.
 *
 * @param serializer      payload serializer, or {@code null} to keep the server default
 * @param deserializer    payload deserializer, or {@code null} to keep the server default
 * @param inputValidator  input schema validator, or {@code null} to keep the server default
 * @param outputValidator output schema validator, or {@code null} to keep the server default
 * @param documentFactory parses/validates encoded JSON documents, or {@code null} to keep the server default
 * @param schemaFactory   parses/validates encoded JSON schemas, or {@code null} to keep the server default
 * @author Konstantin Pavlov
 */
public record JsonConfig(
        @Nullable PayloadSerializer serializer,
        @Nullable PayloadDeserializer deserializer,
        @Nullable JsonSchemaValidator inputValidator,
        @Nullable JsonSchemaValidator outputValidator,
        @Nullable JsonDocumentFactory<String> documentFactory,
        @Nullable JsonSchemaFactory<String> schemaFactory) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link JsonConfig}.
     */
    public static final class Builder {
        private @Nullable PayloadSerializer serializer;
        private @Nullable PayloadDeserializer deserializer;
        private @Nullable JsonSchemaValidator inputValidator;
        private @Nullable JsonSchemaValidator outputValidator;
        private @Nullable JsonDocumentFactory<String> documentFactory;
        private @Nullable JsonSchemaFactory<String> schemaFactory;

        private Builder() {}

        /**
         * Sets both serializer and deserializer from a combined serde.
         */
        public Builder serde(PayloadSerde serde) {
            return serializer(serde).deserializer(serde);
        }

        public Builder serializer(PayloadSerializer serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder deserializer(PayloadDeserializer deserializer) {
            this.deserializer = deserializer;
            return this;
        }

        public Builder schemaValidator(@Nullable JsonSchemaValidator validator) {
            return inputSchemaValidator(validator).outputSchemaValidator(validator);
        }

        public Builder inputSchemaValidator(@Nullable JsonSchemaValidator inputValidator) {
            this.inputValidator = inputValidator;
            return this;
        }

        public Builder outputSchemaValidator(@Nullable JsonSchemaValidator outputValidator) {
            this.outputValidator = outputValidator;
            return this;
        }

        public Builder documentFactory(@Nullable JsonDocumentFactory<String> documentFactory) {
            this.documentFactory = documentFactory;
            return this;
        }

        public Builder schemaFactory(@Nullable JsonSchemaFactory<String> schemaFactory) {
            this.schemaFactory = schemaFactory;
            return this;
        }

        public JsonConfig build() {
            return new JsonConfig(
                    serializer, deserializer, inputValidator, outputValidator, documentFactory, schemaFactory);
        }
    }
}
