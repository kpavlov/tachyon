/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.test;

import static dev.tachyonmcp.core.server.json.JsonUtils.TREE_READ_CONTEXT;

import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.codecs.Codec;
import dev.tachyonmcp.core.server.ServerBuilder;
import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.transport.jsonrpc.JsonRpcCodec;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;

public class TestUtils {

    private TestUtils() {}

    public static ServerEngine newEngine(Consumer<ServerBuilder> configurer) {
        return newEngine(configurer, server -> {});
    }

    public static ServerEngine newEngine(Consumer<ServerBuilder> configurer, Consumer<TachyonServer> registrar) {
        var builder = TachyonServer.builder();
        configurer.accept(builder);
        var server = builder.build();
        try {
            registrar.accept(server);
        } catch (RuntimeException | Error failure) {
            try {
                server.close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        return (ServerEngine) server;
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
