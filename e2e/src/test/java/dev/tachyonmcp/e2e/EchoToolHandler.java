/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import dev.tachyonmcp.server.features.tools.AbstractAsyncToolHandler;
import dev.tachyonmcp.server.features.tools.AsyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.StringNode;

class EchoToolHandler extends AbstractAsyncToolHandler implements AsyncToolHandler<Object, Object> {

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
    public CompletionStage<Object> handleAsync(McpContext context, Object arguments) {
        return CompletableFuture.supplyAsync(() -> {
            if (!(arguments instanceof Map<?, ?> map)) {
                return ToolResult.text("");
            }
            var text = (StringNode) map.get("message");
            return ToolResult.text(text.stringValue());
        });
    }
}
