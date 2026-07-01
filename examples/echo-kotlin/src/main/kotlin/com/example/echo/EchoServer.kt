// Copyright (c) 2026 Konstantin Pavlov.

package com.example.echo

import dev.tachyonmcp.server.McpServerHandle
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.config.Mode
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import dev.tachyonmcp.server.registerTool
import tools.jackson.databind.node.JsonNodeFactory

fun createServer(port: Int = 0): McpServerHandle {
    val inputSchema = buildEchoSchema()
    val handle =
        TachyonServer(port = port) {
            info {
                name = "echo-server"
                title = "Echo Server"
                version = "1.0.0"
                description = "Echo MCP server built with Tachyon Kotlin DSL"
            }
            capabilities {
                tools = Mode.ON
            }
            tool(
                name = "echo",
                description = "Echo message",
                inputSchema = inputSchema,
            ) {
                ToolResult.text(args.string("message"))
            }
        }

    handle
        .server()
        .registerTool(
            ToolDescriptor
                .builder()
                .name("reverse-echo")
                .description("Echo reverse message")
                .inputSchema(inputSchema)
                .build(),
        ) {
            val message = args.string("message")
            ToolResult<*>.text(message.reversed())
        }
    return handle
}

private fun buildEchoSchema() =
    JsonNodeFactory.instance.objectNode().apply {
        put("type", "object")
        putObject("properties").apply {
            putObject("message").apply {
                put("type", "string")
                put("description", "Message to echo")
            }
        }
        putArray("required").add("message")
    }

fun main() {
    val handle = createServer(8080)
    println("Echo server running. Connect your MCP client to http://localhost:${handle.port()}/mcp")
    Thread.sleep(Long.MAX_VALUE)
}
