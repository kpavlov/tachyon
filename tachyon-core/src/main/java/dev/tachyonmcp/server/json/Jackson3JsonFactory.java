/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import tools.jackson.databind.JsonNode;

/**
 * Jackson 3-backed {@link JsonDocumentFactory} and {@link JsonSchemaFactory}: parses the source
 * and rejects malformed JSON before wrapping it. Accepts a raw JSON string, an already-parsed
 * {@link JsonNode}, or a provider-neutral {@link JsonObject} — the latter two are structurally
 * valid by construction, so they're wrapped without re-validation.
 *
 * @author Konstantin Pavlov
 */
public final class Jackson3JsonFactory implements JsonDocumentFactory<String>, JsonSchemaFactory<String> {

    public static final Jackson3JsonFactory INSTANCE = new Jackson3JsonFactory();

    private Jackson3JsonFactory() {}

    @Override
    public JsonDocument toJsonDocument(String json) {
        validate(json);
        return JsonDocument.of(json);
    }

    public JsonDocument toJsonDocument(JsonNode node) {
        return JsonDocument.of(node.toString());
    }

    public JsonDocument toJsonDocument(JsonObject object) {
        return JsonDocument.of(object.json());
    }

    @Override
    public JsonSchema toJsonSchema(String json) {
        validate(json);
        return JsonSchema.of(json);
    }

    public JsonSchema toJsonSchema(JsonNode node) {
        return JsonSchema.of(node.toString());
    }

    public JsonSchema toJsonSchema(JsonObject object) {
        return JsonSchema.of(object.json());
    }

    private static void validate(String json) {
        try {
            JsonUtils.parse(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Not valid JSON: " + json, e);
        }
    }
}
