/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.extensions.tools.echo;

import dev.tachyonmcp.json.JsonSchema;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;

public class EchoToolHandler extends AbstractToolHandler {

    static final JsonSchema ECHO_INPUT_SCHEMA = JsonSchema.of("""
        {
          "type": "object",
          "properties": {
            "message": {
              "type": "string",
              "description": "Message to echo"
            }
          },
          "required": ["message"]
        }
        """);

    public EchoToolHandler() {
        super(ToolDescriptor.builder()
                .name("echo")
                .description("Echo back the input message")
                .inputSchema(ECHO_INPUT_SCHEMA)
                .build());
    }

    @Override
    public ToolResult handle(InteractionContext context, ToolRequest request) {
        return ToolResult.text(request.arguments().stringOr("message", ""));
    }
}
