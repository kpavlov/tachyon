/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.e2e;

import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class EchoToolHandler {

    static final JsonNode ECHO_INPUT_SCHEMA = buildEchoSchema();

    private static JsonNode buildEchoSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var msg = props.putObject("message");
        msg.put("type", "string");
        msg.put("description", "Message to echo");
        var req = schema.putArray("required");
        req.add("message");
        return schema;
    }

    static ToolHandler create() {
        return ToolHandler.ofAsync(
                b -> b.name("echo").description("Echo back the input message").inputSchema(ECHO_INPUT_SCHEMA),
                (ctx, request) -> CompletableFuture.supplyAsync(
                        () -> ToolResult.text(request.arguments().stringOr("message", ""))));
    }
}
