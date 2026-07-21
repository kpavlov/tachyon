/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs;

import static dev.tachyonmcp.server.json.JsonUtils.FACTORY;
import static dev.tachyonmcp.server.json.JsonUtils.TREE_READ_CONTEXT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.core.JsonToken;

public final class ProtocolCodecUtil {

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
}
