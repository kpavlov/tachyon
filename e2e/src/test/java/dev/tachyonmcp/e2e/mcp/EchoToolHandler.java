/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.features.tools.AsyncToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import java.util.concurrent.CompletableFuture;

class EchoToolHandler {

    static final JsonSchema ECHO_INPUT_SCHEMA = JsonSchema.unchecked("""
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

    static final ToolDescriptor DESCRIPTOR = ToolDescriptor.builder()
            .name("echo")
            .description("Echo back the input message")
            .inputSchema(ECHO_INPUT_SCHEMA)
            .build();

    static final AsyncToolFn FN = (ctx, request) -> CompletableFuture.supplyAsync(
            () -> ToolResult.text(request.arguments().stringOr("message", "")));
}
