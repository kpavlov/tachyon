// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.echo

import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.server.config.Mode
import dev.tachyonmcp.kotlin.server.TachyonServer
import dev.tachyonmcp.kotlin.server.buildServer

fun assembleServer(port: Int = 0): TachyonServer {
    val inputSchema = buildEchoSchema()
    val server =
        buildServer {
            network { this.port = port }
            info {
                name = "echo-server"
                title = "Echo Server"
                version = "1.0.0"
                description = "Echo MCP server built with Tachyon Kotlin DSL"
            }
            session {
                enabled = true
            }
            capabilities {
                tools { mode = Mode.ON }
            }
            tool(
                name = "echo",
                description = "Echo message",
                inputSchema = inputSchema,
            ) {
                text(arguments.stringValue("message"))
            }
        }

    server.registerTool(
        name = "reverse-echo",
        description = "Echo reverse message",
        inputSchema = inputSchema,
    ) {
        text(arguments.stringValue("message").reversed())
    }
    return server
}

private fun buildEchoSchema() =
    JsonSchema.of(
        // language=json
        """
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
        """,
    )

fun main() {
    val server = assembleServer(8080)
    server.start()
    println("Echo server running. Connect your MCP client to http://localhost:${server.port()}/mcp")
    Thread.sleep(Long.MAX_VALUE)
}
