/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jspecify.annotations.Nullable;

public record TestMcpClient(int serverPort, HttpClient httpClient) implements Closeable {
    @Override
    public void close() {
        httpClient.close();
    }

    TestMcpClient(int port) {
        this(port, HttpClient.newHttpClient());
    }

    public String initialize() throws Exception {
        // language=JSON
        var initBody = """
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "method":"initialize",
                  "params":{
                    "protocolVersion":"2025-11-25",
                    "capabilities":{},
                    "clientInfo":{
                      "name":"test",
                      "version":"1.0"
                    }
                  }
                }
                """;
        var response = httpClient.send(
                baseRequest()
                        .POST(HttpRequest.BodyPublishers.ofString(initBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
        sendInitialized(sessionId);
        return sessionId;
    }

    public void sendInitialized(String sessionId) throws Exception {
        // language=JSON
        var body = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """;
        httpClient.send(
                baseRequest()
                        .header("MCP-Session-Id", sessionId)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(String body) throws Exception {
        return post(null, body);
    }

    public HttpResponse<String> post(@Nullable String sessionId, String body) throws Exception {
        var builder = baseRequest();
        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId);
        }
        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> sendRequest(String sessionId, String body) throws Exception {
        return post(sessionId, body);
    }

    public HttpResponse<String> delete(String sessionId) throws Exception {
        return httpClient.send(
                baseRequest().header("MCP-Session-Id", sessionId).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder baseRequest() {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-11-25");
    }
}
