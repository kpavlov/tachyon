/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.json;

import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.Map;
import java.util.Optional;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

/**
 * Test-only {@link JsonSchemaFactory} for {@link String} sources, registered via {@code
 * META-INF/services}. Validates the JSON strictly, returns a schema for object or boolean JSON,
 * and returns {@link Optional#empty()} for other valid JSON so the resolution chain falls through.
 */
public final class StringJsonSchemaFactory implements JsonSchemaFactory<String> {

    private final JSONParser parser = new JSONParser(JSONParser.MODE_STRICTEST);

    public StringJsonSchemaFactory() {}

    @Override
    public Class<String> sourceType() {
        return String.class;
    }

    @Override
    public Optional<JsonSchema> toJsonSchema(String json) {
        Object parsed;
        try {
            parsed = parser.parse(json);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Not valid JSON: " + json, e);
        }
        if (parsed instanceof Boolean || parsed instanceof Map) {
            return Optional.of(JsonSchema.unchecked(json));
        }
        return Optional.empty();
    }
}
