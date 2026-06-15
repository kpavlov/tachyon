/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.test;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.Codec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import tools.jackson.databind.JsonNode;

public class TestUtils {

    private TestUtils() {}

    public static JsonNode parseJson(String json) {
        try (var p = Codec.FACTORY.createParser(JsonRpcCodec.TREE_READ_CONTEXT, json)) {
            p.nextToken();
            return JsonRpcCodec.readTreeValue(p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
