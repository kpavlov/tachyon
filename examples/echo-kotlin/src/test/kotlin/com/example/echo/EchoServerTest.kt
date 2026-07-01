// Copyright (c) 2026 Konstantin Pavlov.

package com.example.echo

import io.kotest.assertions.json.shouldEqualJson
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class EchoServerTest {
    companion object {
        private lateinit var handle: dev.tachyonmcp.server.McpServerHandle

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            handle = createServer(0)
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            handle.close()
        }
    }

    @Test
    fun `should list tools`() {
        EchoTestClient(handle.port()).use { client ->
            val sessionId = client.initialize()

            // language=json
            val response =
                client.post(
                    sessionId,
                    """
                    {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                    """.trimIndent(),
                )

            val json = response.body()
            json shouldEqualJson
                """
                {"jsonrpc":"2.0","id":1,"result":{"tools":[
                {
                  "name":"echo",
                  "description":"Echo message",
                  "inputSchema":{
                    "type":"object",
                    "properties": {
                      "message":{"type":"string","description":"Message to echo"}},
                      "required":["message"]}},
                {
                  "name": "reverse-echo",
                  "description": "Echo reverse message",
                  "inputSchema": {
                    "type": "object",
                    "properties": {
                      "message": {
                        "type": "string",
                        "description": "Message to echo"
                      }
                    },
                    "required": [
                      "message"
                    ]
                  }

                }
                          ]}}
                """.trimIndent()
        }
    }

    @Test
    fun `echo tool`() {
        EchoTestClient(handle.port()).use { client ->
            val sessionId = client.initialize()

            // language=json
            val response =
                client.post(
                    sessionId,
                    """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"echo","arguments":{"message":"Hello, MCP!"}}}
                    """.trimIndent(),
                )

            val json = response.body()
            json shouldEqualJson
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "content": [
                      {
                        "type": "text",
                        "text": "Hello, MCP!"
                      }
                    ]
                  }
                }
                """.trimIndent()
        }
    }

    @Test
    fun `reverse echo tool`() {
        EchoTestClient(handle.port()).use { client ->
            val sessionId = client.initialize()

            // language=json
            val response =
                client.post(
                    sessionId,
                    """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"reverse-echo","arguments":{"message":"stressed"}}}
                    """.trimIndent(),
                )

            val json = response.body()
            json shouldEqualJson
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "content": [
                      {
                        "type": "text",
                        "text": "desserts"
                      }
                    ]
                  }
                }
                """.trimIndent()
        }
    }

    @Test
    fun `should return error when message missing`() {
        EchoTestClient(handle.port()).use { client ->
            val sessionId = client.initialize()

            // language=json
            val response =
                client.post(
                    sessionId,
                    """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"echo","arguments":{}}}
                    """.trimIndent(),
                )

            response.body() shouldEqualJson
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "error": {
                    "code": -32600,
                    "message": "required property 'message' not found"
                  }
                }
                """.trimIndent()
        }
    }

    @Test
    fun `should respond to initialize`() {
        EchoTestClient(handle.port()).use { client ->
            // language=json
            val response =
                client.post(
                    null,
                    """
                    {"jsonrpc":"2.0",
                      "id":1,
                      "method":"initialize",
                      "params":{"protocolVersion":"2025-11-25","capabilities":{},
                               "clientInfo":{"name":"test","version":"1.0"}}}
                    """.trimIndent(),
                )

            response.body() shouldEqualJson
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "protocolVersion": "2025-11-25",
                    "capabilities": {
                      "tools": {}
                    },
                    "serverInfo": {
                      "version": "1.0.0",
                      "description": "Echo MCP server built with Tachyon Kotlin DSL",
                      "name": "echo-server",
                      "title": "Echo Server"
                    }
                  }
                }
                """.trimIndent()
        }
    }
}
