/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import static dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec.readTreeValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TreeNode;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;

public final class ProtocolCodecUtil {

    private static final JsonFactory FACTORY = new JsonFactory();
    private static final ObjectReadContext TREE_READ_CONTEXT = new ObjectReadContext.Base() {
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

    private ProtocolCodecUtil() {}

    public static <T> T decodeWithCodec(String json, Class<T> targetType) {
        try {
            var codec = CodecRegistry.codecFor(targetType);
            try (var p = FACTORY.createParser(TREE_READ_CONTEXT, json.getBytes(StandardCharsets.UTF_8))) {
                if (p.nextToken() != JsonToken.START_OBJECT) {
                    throw new IOException("Expected JSON object");
                }
                return codec.decode(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decode " + targetType.getSimpleName(), e);
        }
    }

    public static JsonNode parseJsonNode(String json) {
        try (var p = FACTORY.createParser(TREE_READ_CONTEXT, json.getBytes(StandardCharsets.UTF_8))) {
            p.nextToken();
            return readTreeValue(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSON", e);
        }
    }
}
