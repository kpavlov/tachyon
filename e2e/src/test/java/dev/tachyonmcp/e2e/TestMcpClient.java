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
import tools.jackson.databind.ObjectMapper;

public final class TestMcpClient implements Closeable {
    private static final Duration DEFAULT_TASK_POLL_INTERVAL = Duration.ofMillis(100);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int serverPort;
    private final HttpClient httpClient;
    private volatile @Nullable String sessionId;

    TestMcpClient(int port) {
        this.serverPort = port;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void close() {
        httpClient.close();
    }

    public @Nullable String initialize() throws Exception {
        sessionId = null;
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
        assertThat(response.statusCode())
                .as("initialize response: %s", response.body())
                .isEqualTo(200);
        var responseJson = MAPPER.readTree(response.body());
        assertThat(responseJson.path("result").path("protocolVersion").asString())
                .as("negotiated protocol version in: %s", response.body())
                .isEqualTo("2025-11-25");
        var sessionId = response.headers().firstValue("MCP-Session-Id").orElse(null);
        sendInitialized(sessionId);
        return sessionId;
    }

    public void sendInitialized(@Nullable String sessionId) throws Exception {
        var response = post(sessionId, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);
        assertThat(response.statusCode())
                .as("notifications/initialized response: %s", response.body())
                .isEqualTo(202);
        assertThat(response.body())
                .as("notifications/initialized response body")
                .isEmpty();
        this.sessionId = sessionId;
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

    public HttpResponse<String> ping(@Nullable String sessionId, Object id) throws Exception {
        final String idString;
        if (id instanceof Integer) {
            idString = id.toString();
        } else {
            idString = "\"" + id + "\"";
        }
        return post(sessionId, "{\"jsonrpc\":\"2.0\",\"id\":%s,\"method\":\"ping\"}".formatted(idString));
    }

    public HttpResponse<Stream<String>> sendStreamingRequest(@Nullable String sessionId, @Language("json") String body)
            throws Exception {
        var builder = baseRequest();
        if (sessionId != null) builder.header("MCP-Session-Id", sessionId);
        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofLines());
    }

    /**
     * {@link #sendRpc(String, String)} against the session set by {@link #initialize()}.
     */
    public String sendRpc(@Language("json") String body) throws Exception {
        return sendRpc(sessionId, body);
    }

    /**
     * Sends a JSON-RPC request and returns the response body, extracting the JSON-RPC envelope
     * from an SSE body if the response content-type is {@code text/event-stream}.
     */
    public String sendRpc(@Nullable String sessionId, @Language("json") String body) throws Exception {
        var response = post(sessionId, body);
        assertThat(response.statusCode()).isEqualTo(200);
        var contentType = response.headers().firstValue("content-type").orElse("");
        if (contentType.startsWith("text/event-stream")) {
            return AbstractMcpE2eTest.extractJsonRpcResponse(
                    response.body(), AbstractMcpE2eTest.extractRequestId(body));
        }
        return response.body();
    }

    /**
     * Sends {@code tasks/get} for {@code taskId} and returns the JSON-RPC response body.
     */
    public String getTask(@Nullable String sessionId, String taskId) throws Exception {
        // language=JSON
        var body = """
                {"jsonrpc":"2.0","id":"tasks-get","method":"tasks/get","params":{"taskId":%s}}
                """.formatted(MAPPER.writeValueAsString(taskId));
        return sendRpc(sessionId, body);
    }

    /**
     * Polls {@code tasks/get} until the task reaches {@code status}, then returns the last
     * response body. Fails if {@code status} is not reached within {@code timeout}.
     */
    public String awaitTaskStatus(@Nullable String sessionId, String taskId, String status, Duration timeout) {
        var lastResponse = new AtomicReference<@Nullable String>();
        var nextPollInterval = new AtomicReference<>(DEFAULT_TASK_POLL_INTERVAL);
        await().alias("task %s to reach status %s".formatted(taskId, status))
                .atMost(timeout)
                .pollDelay(Duration.ZERO)
                .pollInterval((pollCount, previousDuration) -> nextPollInterval.get())
                .until(() -> {
                    var json = getTask(sessionId, taskId);
                    lastResponse.set(json);
                    var snapshot = taskSnapshot(json, taskId);
                    if (snapshot.pollInterval() != null) {
                        nextPollInterval.set(snapshot.pollInterval());
                    }
                    return snapshot.status().equals(status);
                });
        return lastResponse.get();
    }

    /** {@link #awaitTaskStatus(String, String, String, Duration)} with a 5s timeout. */
    public String awaitTaskStatus(String sessionId, String taskId, String status) {
        return awaitTaskStatus(sessionId, taskId, status, Duration.ofSeconds(5));
    }

    /** {@link #awaitTaskStatus(String, String, String, Duration)} against the stored session. */
    public String awaitTaskStatus(String taskId, String status, Duration timeout) {
        return awaitTaskStatus(sessionId, taskId, status, timeout);
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

    private static TaskSnapshot taskSnapshot(String json, String taskId) {
        var response = MAPPER.readTree(json);
        assertThat(response.path("id").asString())
                .as("tasks/get response id in: %s", json)
                .isEqualTo("tasks-get");
        var result = response.path("result");
        assertThat(result.isObject()).as("tasks/get result in: %s", json).isTrue();
        assertThat(result.path("taskId").asString())
                .as("tasks/get taskId in: %s", json)
                .isEqualTo(taskId);
        var status = result.path("status").asString(null);
        assertThat(status).as("tasks/get status in: %s", json).isNotNull();
        var pollIntervalNode = result.path("pollInterval");
        var pollInterval = pollIntervalNode.isNumber() && pollIntervalNode.asLong() > 0
                ? Duration.ofMillis(pollIntervalNode.asLong())
                : null;
        return new TaskSnapshot(status, pollInterval);
    }

    private record TaskSnapshot(String status, @Nullable Duration pollInterval) {}
}
