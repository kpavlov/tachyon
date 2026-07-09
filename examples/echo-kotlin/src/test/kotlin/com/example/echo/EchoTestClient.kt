// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.echo

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class EchoTestClient(port: Int) : AutoCloseable {

    private val httpClient = HttpClient.newHttpClient()
    private val baseUri = URI.create("http://localhost:$port/mcp")

    fun initialize(): String {
        val response = post(null, """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-11-25","capabilities":{},
                       "clientInfo":{"name":"test","version":"1.0"}}}
            """.trimIndent())
        val sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow()
        sendInitialized(sessionId)
        return sessionId
    }

    private fun sendInitialized(sessionId: String) {
        post(sessionId, """
            {"jsonrpc":"2.0","method":"notifications/initialized"}
            """.trimIndent())
    }

    fun post(sessionId: String?, body: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(baseUri)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2025-11-25")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId)
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    override fun close() {
        httpClient.close()
    }
}
