/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.extensions.tools.echo;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public class EchoToolHandler extends AbstractSyncToolHandler {

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

    public EchoToolHandler() {
        super(ToolDescriptor.builder()
                .name("echo")
                .description("Echo back the input message")
                .inputSchema(ECHO_INPUT_SCHEMA)
                .build());
    }

    @Override
    public ToolResult handle(InteractionContext context, ToolArgs args) {
        return ToolResult.text(args.stringOr("message", ""));
    }
}
