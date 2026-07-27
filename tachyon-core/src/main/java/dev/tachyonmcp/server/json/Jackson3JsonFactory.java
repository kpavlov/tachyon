/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.server.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.server.json.spi.JsonSchemaFactory;

/**
 * Jackson 3-backed {@link JsonDocumentFactory} and {@link JsonSchemaFactory} for {@link String}
 * sources: parses the source and rejects malformed JSON before wrapping it.
 *
 * <p>Registered via {@link java.util.ServiceLoader} in {@code META-INF/services}. For
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

    /**
     * Provider factory used by {@link java.util.ServiceLoader} to obtain the singleton.
     */
    public static Jackson3JsonFactory provider() {
        return INSTANCE;
    }

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
