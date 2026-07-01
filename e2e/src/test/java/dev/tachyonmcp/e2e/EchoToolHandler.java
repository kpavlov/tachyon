/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.e2e;

import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class EchoToolHandler extends AbstractAsyncToolHandler {

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

    EchoToolHandler() {
        super(ToolDescriptor.builder("echo")
                .description("Echo back the input message")
                .inputSchema(ECHO_INPUT_SCHEMA)
                .build());
    }

    @Override
    public CompletionStage<? extends ToolResult<?>> handleAsync(McpContext context, ToolArgs args) {
        return CompletableFuture.supplyAsync(() -> ToolResult.text(args.stringOr("message", "")));
    }
}
