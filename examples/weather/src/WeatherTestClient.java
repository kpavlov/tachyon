/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package com.example.weather;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jspecify.annotations.Nullable;

public class WeatherTestClient implements Closeable {

    private final int port;
    private final HttpClient httpClient;

    WeatherTestClient(int port) {
        this.port = port;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void close() {
        httpClient.close();
    }

    public String initialize() throws Exception {
        var response = post(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize",
                 "params":{"protocolVersion":"2025-11-25","capabilities":{},
                           "clientInfo":{"name":"test","version":"1.0"}}}
                """);
        var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
        sendInitialized(sessionId);
        return sessionId;
    }

    public void sendInitialized(String sessionId) throws Exception {
        post(sessionId, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);
    }

    public HttpResponse<String> post(@Nullable String sessionId, String body) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-11-25")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static void main(String[] args) throws Exception {
        var port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        var client = new WeatherTestClient(port);
        var sessionId = client.initialize();

        var toolsResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);
        System.out.println("=== Tools ===");
        System.out.println(toolsResponse.body());

        var callResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"get-weather","arguments":{"city":"London","units":"celsius"}}}
                """);
        System.out.println("\n=== Weather Tool Result ===");
        System.out.println(callResponse.body());

        var resourcesResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"resources/list"}
                """);
        System.out.println("\n=== Resources ===");
        System.out.println(resourcesResponse.body());

        var promptsResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":5,"method":"prompts/list"}
                """);
        System.out.println("\n=== Prompts ===");
        System.out.println(promptsResponse.body());
    }
}
