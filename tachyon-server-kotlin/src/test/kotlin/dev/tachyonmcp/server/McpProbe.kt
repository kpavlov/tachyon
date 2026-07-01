// Copyright (c) 2026 Konstantin Pavlov.

package dev.tachyonmcp.server

import java.io.Closeable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Minimal raw-HTTP MCP client for probing a bound server in tests. */
internal class McpProbe(
    private val port: Int,
) : Closeable {
    private val http = HttpClient.newHttpClient()
    private var sessionId: String? = null

    fun initialize(): HttpResponse<String> {
        val response =
            post(
                """
                {"jsonrpc":"2.0","id":1,"method":"initialize",
                 "params":{"protocolVersion":"2025-11-25","capabilities":{},
                           "clientInfo":{"name":"kt-probe","version":"1.0"}}}
                """.trimIndent(),
            )
        sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow()
        post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        return response
    }

    fun callTool(
        name: String,
        arguments: String = "{}",
    ): HttpResponse<String> =
        post(
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}""",
        )

    fun request(
        id: Int,
        method: String,
    ): HttpResponse<String> = post("""{"jsonrpc":"2.0","id":$id,"method":"$method"}""")

    fun post(body: String): HttpResponse<String> {
        val builder =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-11-25")
                .POST(HttpRequest.BodyPublishers.ofString(body))
        sessionId?.let { builder.header("MCP-Session-Id", it) }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    override fun close() {
        http.close()
    }
}
