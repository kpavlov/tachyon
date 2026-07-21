/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.test;

import static dev.tachyonmcp.server.json.JsonUtils.TREE_READ_CONTEXT;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.Codec;
import dev.tachyonmcp.server.ServerBuilder;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.internal.ServerEngine;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;

public class TestUtils {

    private TestUtils() {}

    public static ServerEngine newEngine(Consumer<ServerBuilder> configurer) {
        var builder = TachyonServer.builder();
        configurer.accept(builder);
        return (ServerEngine) builder.build();
    }

    public static JsonNode parseJson(String json) {
        try (var p = Codec.FACTORY.createParser(TREE_READ_CONTEXT, json)) {
            p.nextToken();
            return JsonRpcCodec.readTreeValue(p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
