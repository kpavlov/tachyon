/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.json;

import static dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec.readTreeValue;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.domain.ContentBlock;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TreeNode;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@InternalApi
public final class JsonUtils {

    private static final JsonMapper MAPPER = new JsonMapper();
    public static final JsonFactory FACTORY = new JsonFactory();

    public static final ObjectReadContext TREE_READ_CONTEXT = new ObjectReadContext.Base() {
        @Override
        @SuppressWarnings("unchecked")
        public <T extends TreeNode> T readTree(JsonParser p) {
            try {
                return (T) readTreeValue(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    private JsonUtils() {}

    static JsonMapper mapper() {
        return MAPPER;
    }

    public static JsonNode parse(String json) {
        return MAPPER.readTree(json);
    }

    public static String writeString(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static JsonNode parseJsonNode(String json) {
        try (var p = FACTORY.createParser(TREE_READ_CONTEXT, json)) {
            p.nextToken();
            return readTreeValue(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSON", e);
        }
    }

    /**
     * Converts a given value into a {@link JsonNode} representing an object, if applicable.
     * The method supports the following input types:
     * - {@link RawJson}, where the contained JSON string is parsed into a {@link JsonNode}.
     * - {@link JsonNode}, if it is already an object node.
     * - {@link java.util.Map}, where its entries are transformed into an object node.
     * - Other non-null values: serialized via the provided {@link PayloadSerializer}.
     *
     * @param <T>        the type of the value to be converted
     * @param value      the value to be converted
     * @param serializer the serializer for non-tree values
     * @return a {@link JsonNode} object node representation of the value, or {@code null} if the value cannot be converted
     */
    public static @Nullable <T> JsonNode valueToObjectNode(@Nullable T value, PayloadSerializer serializer) {
        if (value instanceof RawJson(String json)) {
            var node = JsonUtils.parse(json);
            return node.isObject() ? node : null;
        }
        if (value instanceof JsonNode node) {
            return node.isObject() ? node : null;
        }
        if (value instanceof Map<?, ?> map) {
            var contentNode = JsonNodeFactory.instance.objectNode();
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof String k) {
                    var v = entry.getValue();
                    if (v instanceof JsonNode jn) {
                        contentNode.set(k, jn);
                    } else if (v != null) {
                        contentNode.set(k, JsonUtils.parseJsonNode(JsonRpcCodec.writeValueAsString(v)));
                    }
                }
            }
            return contentNode;
        }
        if (value != null) {
            var json = serializer.serialize(value);
            var node = MAPPER.readTree(json);
            return node.isObject() ? node : null;
        }
        return null;
    }

    /**
     * Serializes non-tree structured values in a {@link ToolResult} into {@link RawJson}.
     * {@link JsonNode} and {@link RawJson} pass through. Maps carrying {@link JsonNode} values
     * are serialized with Jackson regardless of the configured serializer — a non-Jackson serde
     * cannot encode Jackson trees.
     */
    public static ToolResult serializeStructured(ToolResult result, PayloadSerializer serializer) {
        if (result instanceof ToolResult.WithMeta(ToolResult inner1, Map<String, JsonNode> meta)) {
            var inner = serializeStructured(inner1, serializer);
            return inner == inner1 ? result : new ToolResult.WithMeta(inner, meta);
        }
        if (!(result instanceof ToolResult.Success(Object sv, List<ContentBlock> content))) return result;
        if (sv == null || sv instanceof RawJson || sv instanceof JsonNode) return result;
        var json = containsJsonNodes(sv) ? MAPPER.writeValueAsString(sv) : serializer.serialize(sv);
        return new ToolResult.Success(RawJson.of(json), content);
    }

    private static boolean containsJsonNodes(Object structuredValue) {
        return structuredValue instanceof Map<?, ?> map && map.values().stream().anyMatch(v -> v instanceof JsonNode);
    }
}
