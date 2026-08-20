/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import dev.tachyonmcp.api.annotations.InternalApi;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.spi.JsonSchemaFactory;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link Map}-backed {@link JsonSchemaFactory}: converts a plain {@code Map<String, Object>} into
 * an {@link JsonSchema}.
 *
 * @author Konstantin Pavlov
 */
@InternalApi
public final class MapJsonFactory implements JsonSchemaFactory<Map<String, Object>> {

    public static final MapJsonFactory INSTANCE = new MapJsonFactory();

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> SOURCE_TYPE = (Class<Map<String, Object>>) (Class<?>) Map.class;

    public MapJsonFactory() {}

    @Override
    public Class<Map<String, Object>> sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public Optional<JsonSchema> toJsonSchema(Map<String, Object> source) {
        ObjectNode node = JsonUtils.mapper().valueToTree(source);
        return Optional.of(new JacksonObjectJsonSchema(node));
    }
}
