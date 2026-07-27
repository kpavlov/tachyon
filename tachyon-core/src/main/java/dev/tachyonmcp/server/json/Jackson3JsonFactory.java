/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.protocol.api.json.JsonDocument;
import dev.tachyonmcp.protocol.api.json.JsonObject;
import dev.tachyonmcp.protocol.api.json.JsonSchema;
import dev.tachyonmcp.protocol.api.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.protocol.api.json.spi.JsonSchemaFactory;

/**
 * Jackson 3-backed {@link JsonDocumentFactory} and {@link JsonSchemaFactory} for {@link String}
 * sources: parses the source and rejects malformed JSON before wrapping it.
 *
 * <p>Registered via {@link java.util.ServiceLoader} in {@code META-INF/services} (discovered
 * through its public no-arg constructor, not {@link #INSTANCE} — the module isn't an explicit
 * module, so {@code ServiceLoader} never looks for a {@code provider()} factory method). For
 * already-parsed Jackson {@code JsonNode} trees or tachyon's provider-neutral {@link JsonObject},
 * see {@link JacksonNodeJsonFactory} and {@link JacksonObjectJsonFactory} — a single class can't
 * implement {@code JsonSchemaFactory<String>} and {@code JsonSchemaFactory<JsonNode>} at once, since
 * Java forbids implementing the same generic interface twice with different type arguments.
 *
 * @author Konstantin Pavlov
 */
public final class Jackson3JsonFactory implements JsonDocumentFactory<String>, JsonSchemaFactory<String> {

    public static final Jackson3JsonFactory INSTANCE = new Jackson3JsonFactory();

    public Jackson3JsonFactory() {}

    @Override
    public Class<String> sourceType() {
        return String.class;
    }

    @Override
    public JsonDocument toJsonDocument(String json) {
        validate(json);
        return JsonDocument.of(json);
    }

    @Override
    public JsonSchema toJsonSchema(String json) {
        validate(json);
        return JsonSchema.of(json);
    }

    private static void validate(String json) {
        try {
            JsonUtils.parse(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Not valid JSON: " + json, e);
        }
    }
}
