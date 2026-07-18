/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;

public record TestMcpClient(int serverPort, HttpClient httpClient, AtomicReference<@Nullable String> session)
        implements Closeable {
    @Override
    public void close() {
        httpClient.close();
    }

    TestMcpClient(int port) {
        this(port, HttpClient.newHttpClient(), new AtomicReference<>());
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
        session.set(sessionId);
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

    public HttpResponse<String> post(@Language("json") String body) throws Exception {
        return post(null, body);
    }

    /**
     * POSTs an MCP request carrying an {@code Origin} header. Used to exercise the
     * DNS-rebinding protection, which validates {@code Origin} before {@code Host}.
     */
    public HttpResponse<String> postWithOrigin(String origin, @Language("json") String body) throws Exception {
        return httpClient.send(
                baseRequest()
                        .header("Origin", origin)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(@Nullable String sessionId, @Language("json") String body) throws Exception {
        var builder = baseRequest();
        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId);
        }
        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<Stream<String>> sendStreamingRequest(@Nullable String sessionId, @Language("json") String body)
            throws Exception {
        var builder = baseRequest();
        if (sessionId != null) builder.header("MCP-Session-Id", sessionId);
        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofLines());
    }

    /** {@link #sendRpc(String, String)} against the session set by {@link #initialize()}. */
    public String sendRpc(@Language("json") String body) throws Exception {
        return sendRpc(session.get(), body);
    }

    /**
     * Sends a JSON-RPC request and returns the response body, extracting the JSON-RPC envelope
     * from an SSE body if the response content-type is {@code text/event-stream}.
     */
    public String sendRpc(String sessionId, @Language("json") String body) throws Exception {
        var response = post(sessionId, body);
        assertThat(response.statusCode()).isEqualTo(200);
        var contentType = response.headers().firstValue("content-type").orElse("");
        if (contentType.startsWith("text/event-stream")) {
            return AbstractMcpE2eTest.extractJsonRpcResponse(
                    response.body(), AbstractMcpE2eTest.extractRequestId(body));
        }
        return response.body();
    }

    /** Sends {@code tasks/get} for {@code taskId} and returns the JSON-RPC response body. */
    public String getTask(String sessionId, String taskId) throws Exception {
        // language=JSON
        var body = """
                {"jsonrpc":"2.0","id":"tasks-get","method":"tasks/get","params":{"taskId":"%s"}}
                """.formatted(taskId);
        return sendRpc(sessionId, body);
    }

    /**
     * Polls {@code tasks/get} until the task reaches {@code status}, then returns the last
     * response body. Fails if {@code status} is not reached within {@code timeout}.
     */
    public String awaitTaskStatus(String sessionId, String taskId, String status, Duration timeout) {
        var statusMarker = "\"status\":\"" + status + "\"";
        var lastResponse = new AtomicReference<String>();
        await().atMost(timeout).pollInterval(Duration.ofMillis(100)).until(() -> {
            var json = getTask(sessionId, taskId);
            lastResponse.set(json);
            return json.contains(statusMarker);
        });
        return lastResponse.get();
    }

    /** {@link #awaitTaskStatus(String, String, String, Duration)} with a 5s timeout. */
    public String awaitTaskStatus(String sessionId, String taskId, String status) {
        return awaitTaskStatus(sessionId, taskId, status, Duration.ofSeconds(5));
    }

    /** {@link #awaitTaskStatus(String, String, String, Duration)} against the stored session. */
    public String awaitTaskStatus(String taskId, String status, Duration timeout) {
        return awaitTaskStatus(session.get(), taskId, status, timeout);
    }

    /** {@link #awaitTaskStatus(String, String, String)} against the stored session, 5s timeout. */
    public String awaitTaskStatus(String taskId, String status) {
        return awaitTaskStatus(taskId, status, Duration.ofSeconds(5));
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
