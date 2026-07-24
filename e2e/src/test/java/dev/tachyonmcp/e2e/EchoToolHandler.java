/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.e2e;

import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JsonSchema;
import java.util.concurrent.CompletableFuture;

class EchoToolHandler {

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

    static ToolHandler create() {
        return ToolHandler.ofAsync(
                b -> b.name("echo").description("Echo back the input message").inputSchema(ECHO_INPUT_SCHEMA),
                (ctx, request) -> CompletableFuture.supplyAsync(
                        () -> ToolResult.text(request.arguments().stringOr("message", ""))));
    }
}
