/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.spi.JsonDocumentFactory;
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.Optional;

/**
 * Jackson 3-backed {@link JsonDocumentFactory} and {@link JsonSchemaFactory} for {@link String}
 * sources: parses the source and rejects malformed JSON before wrapping it.
 *
 * <p>Registered via {@link java.util.ServiceLoader} in {@code META-INF/services} (discovered
 * through its public no-arg constructor, not {@link #INSTANCE} — the module isn't an explicit
 * module, so {@code ServiceLoader} never looks for a {@code provider()} factory method). For
 * already-parsed Jackson {@code JsonNode} trees or tachyon's provider-neutral {@link JsonObject},
 * see {@link JacksonNodeJsonFactory} and {@link JacksonObjectJsonFactory}. The unified {@link
 * JsonSchemaFactory} accepts any source type through {@link #toJsonSchema(Object, Class)}, so a
 * single class can cover several source types.
 *
 * @author Konstantin Pavlov
 */
public final class Jackson3JsonFactory implements JsonDocumentFactory<String>, JsonSchemaFactory {

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
    public Optional<JsonSchema> toJsonSchema(Object source, Class<?> type) {
        if (type != String.class) {
            return Optional.empty();
        }
        var json = (String) source;
        validate(json);
        return Optional.of(JsonSchema.of(json));
    }

    /**
     * Convenience overload parsing a schema directly from a JSON string, rejecting malformed
     * input. Equivalent to {@code toJsonSchema(source, String.class).orElseThrow()}.
     */
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
