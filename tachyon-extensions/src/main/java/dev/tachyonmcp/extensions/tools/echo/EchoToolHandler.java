/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.extensions.tools.echo;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JsonSchema;

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
    public ToolResult handle(InteractionContext context, Args args) {
        return ToolResult.text(args.stringOr("message", ""));
    }
}
