/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;
import me.kpavlov.finchly.queue.MessageAggregator;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base HTTP client for driving a Tachyon MCP server over Streamable HTTP.
 *
 * <p>A subclass fixes the protocol version via {@link #protocolVersion()} and may override {@link
 * #requestBody(String)} / {@link #configureRequest(HttpRequest.Builder, String)} to shape requests
 * for that revision. Instantiate via {@link McpTestClients} or directly with the server's bound
 * port. Use {@code port()} on a {@code port(0)}-started MCP Server.
 */
public abstract class McpClient implements Closeable {

    private static final Duration DEFAULT_TASK_POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration DEFAULT_NOTIFICATION_TIMEOUT = Duration.ofSeconds(5);
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI mcpEndpoint;
    private final HttpClient httpClient;
    private final MessageAggregator<JsonNode> notifications = new MessageAggregator<>();
    private final List<SseStream> openGetStreams = new CopyOnWriteArrayList<>();
    private volatile @Nullable String sessionId;
    private volatile boolean closed;

    /** Creates a client against a local, port-0-style Tachyon server ({@code http://localhost:<port>/mcp}). */
    protected McpClient(int port) {
        this(localEndpoint(port));
    }

    /** Creates a client against an arbitrary MCP endpoint (local or remote, http or https). */
    protected McpClient(URI mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
        this.httpClient = HttpClient.newHttpClient();
    }

    static URI localEndpoint(int port) {
        return URI.create("http://localhost:" + port + "/mcp");
    }

    /** The MCP protocol version this client speaks; sent as {@code MCP-Protocol-Version}. */
    protected abstract String protocolVersion();

    @Override
    public void close() {
        closed = true;
        openGetStreams.forEach(SseStream::close);
        httpClient.close();
    }

    /**
     * Opens a raw GET stream against this client's session, for reconnect / {@code Last-Event-ID}
     * testing. Requires {@link #initialize()} (or {@link #sendInitialized}) to have set a session
     * id first. Closed automatically when this client is {@link #close() closed}, in addition to
     * whatever the caller does with it directly.
     */
    public SseStream openGetStream(@Nullable String lastEventId) {
        return openGetStream(
                Objects.requireNonNull(sessionId, "call initialize() before openGetStream()"), lastEventId);
    }

    /** {@link #openGetStream(String)} against an explicit session id rather than this client's stored one. */
    public SseStream openGetStream(String sessionId, @Nullable String lastEventId) {
        var subscriber = new SseStream(mcpEndpoint.getPort(), sessionId, lastEventId, protocolVersion());
        openGetStreams.add(subscriber);
        subscriber.start();
        return subscriber;
    }

    /**
     * Performs the {@code initialize}/{@code notifications/initialized} handshake and returns the
     * session id issued by the server.
     *
     * @throws UnsupportedOperationException on protocol revisions that removed {@code initialize}
     */
    public @Nullable String initialize() throws Exception {
        sessionId = null;
        // language=JSON
        var initBody = """
            {
              "jsonrpc":"2.0",
              "id":1,
              "method":"initialize",
              "params":{
                "protocolVersion":"%s",
                "capabilities":{},
                "clientInfo":{
                  "name":"test",
                  "version":"1.0"
                }
              }
            }
            """.formatted(protocolVersion());
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

    /**
     * Sends the {@code notifications/initialized} notification for the given session.
     *
     * @param sessionId the session id returned by {@link #initialize()}, or {@code null}
     */
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

    /** POSTs an MCP request without a session. */
    public HttpResponse<String> post(@Language("json") String body) throws Exception {
        return post(null, body);
    }

    /**
     * POSTs an MCP request carrying extra HTTP headers (e.g. {@code Mcp-Param-*} custom headers).
     */
    public HttpResponse<String> post(@Language("json") String body, Map<String, String> extraHeaders) throws Exception {
        return post(null, body, extraHeaders);
    }

    /** POSTs an MCP request for the given session. */
    public HttpResponse<String> post(@Nullable String sessionId, String body) throws Exception {
        return post(sessionId, body, Map.of());
    }

    /** POSTs an MCP request for the given session with extra HTTP headers. */
    public HttpResponse<String> post(@Nullable String sessionId, String body, Map<String, String> extraHeaders)
            throws Exception {
        body = requestBody(body);
        var builder = requestBuilder(body);
        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId);
        }
        extraHeaders.forEach(builder::header);
        var response = httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        captureNotifications(response.body());
        return response;
    }

    /**
     * Parses server-to-client notifications out of an SSE {@code data:} payload and stores them for
     * {@link #awaitNotification(String)}. A JSON-RPC envelope is a notification when it carries a
     * {@code method} and no {@code id}.
     */
    private void captureNotifications(String sseBody) {
        for (var line : sseBody.split("\n")) {
            String data = null;
            if (line.startsWith("data: ")) data = line.substring("data: ".length());
            else if (line.startsWith("data:")) data = line.substring("data:".length());
            if (data == null) continue;
            try {
                var envelope = MAPPER.readTree(data);
                if (envelope.has("method") && !envelope.has("id")) {
                    notifications.push(envelope);
                }
            } catch (Exception e) {
                assertThat(e)
                        .as("SSE data line must be a JSON envelope: %s", data)
                        .isNull();
            }
        }
    }

    /**
     * POSTs an MCP request carrying an {@code Origin} header, exercising DNS-rebinding protection.
     */
    public HttpResponse<String> postWithOrigin(String origin, String body) throws Exception {
        body = requestBody(body);
        var response = httpClient.send(
                requestBuilder(body)
                        .header("Origin", origin)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        captureNotifications(response.body());
        return response;
    }

    /** Posts a {@code ping} request with the given JSON-RPC {@code id}. */
    public HttpResponse<String> ping(@Nullable String sessionId, Object id) throws Exception {
        final var idString = MAPPER.writeValueAsString(id);
        return post(sessionId, "{\"jsonrpc\":\"2.0\",\"id\":%s,\"method\":\"ping\"}".formatted(idString));
    }

    /** Sends a notification (a JSON-RPC envelope with a {@code method} and no {@code id}). */
    public HttpResponse<String> notify(String method) throws Exception {
        return notify(method, null);
    }

    /** Sends a notification with the given params. */
    public HttpResponse<String> notify(String method, @Nullable Object params) throws Exception {
        return post(sessionId, notificationEnvelope(method, params));
    }

    /** Builds the JSON-RPC notification envelope sent by {@link #notify(String, Object)}. */
    static String notificationEnvelope(String method, @Nullable Object params) {
        var paramsJson = params == null ? "" : ",\"params\":" + MAPPER.writeValueAsString(params);
        return "{\"jsonrpc\":\"2.0\",\"method\":\"%s\"%s}".formatted(method, paramsJson);
    }

    /**
     * Awaits a notification with the given {@code method} and returns it once it arrives, or fails
     * after {@code timeout}. The notification is left in the queue (use {@link #clearNotifications()}
     * to reset).
     */
    public Notification awaitNotification(String method, Duration timeout) {
        return awaitNotification(method, node -> true, timeout);
    }

    /**
     * Awaits a notification whose {@code method} matches and whose params satisfy {@code predicate},
     * or fails after {@code timeout}.
     */
    public Notification awaitNotification(String method, Predicate<JsonNode> predicate, Duration timeout) {
        final var message = notifications.awaitMessage(timeout, true, node -> {
            if (closed) {
                throw new IllegalStateException("McpClient closed while awaiting " + method);
            }
            return method.equals(node.path("method").asString()) && predicate.test(node.path("params"));
        });
        return Notification.from(message);
    }

    /** {@link #awaitNotification(String, Duration)} with a 5s timeout. */
    public Notification awaitNotification(String method) {
        return awaitNotification(method, DEFAULT_NOTIFICATION_TIMEOUT);
    }

    /** A snapshot of all notifications received so far, in arrival order. */
    public List<Notification> notifications() {
        return notifications.findAll(node -> true).stream()
                .map(Notification::from)
                .toList();
    }

    /** Removes all received notifications. */
    public void clearNotifications() {
        notifications.clear();
    }

    /** POSTs a request and returns the raw response line stream (for SSE responses). */
    public HttpResponse<Stream<String>> sendStreamingRequest(@Nullable String sessionId, @Language("json") String body)
            throws Exception {
        body = requestBody(body);
        var builder = requestBuilder(body);
        if (sessionId != null) builder.header("MCP-Session-Id", sessionId);
        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofLines());
    }

    /**
     * {@link #sendRpc(String, String)} against the session set by {@link #initialize()}.
     */
    public String sendRpc(String body) throws Exception {
        return sendRpc(sessionId, body);
    }

    /**
     * Sends a JSON-RPC request and returns the response body, extracting the JSON-RPC envelope from
     * an SSE body if the response content-type is {@code text/event-stream}.
     */
    public String sendRpc(@Nullable String sessionId, String body) throws Exception {
        var response = post(sessionId, body);
        assertThat(response.statusCode()).isEqualTo(200);
        var contentType = response.headers().firstValue("content-type").orElse("");
        if (contentType.startsWith("text/event-stream")) {
            return extractJsonRpcResponse(response.body(), extractRequestId(body));
        }
        return response.body();
    }

    /**
     * Sends {@code tasks/get} for {@code taskId} and returns the JSON-RPC response body.
     */
    public String getTask(@Nullable String sessionId, String taskId) throws Exception {
        var body = """
                {"jsonrpc":"2.0","id":"tasks-get","method":"tasks/get","params":{"taskId":%s}}
                """.formatted(MAPPER.writeValueAsString(taskId));
        return sendRpc(sessionId, body);
    }

    /**
     * Polls {@code tasks/get} until the task reaches {@code status}, then returns the last response
     * body. Fails if {@code status} is not reached within {@code timeout}.
     */
    public @Nullable String awaitTaskStatus(
            @Nullable String sessionId, String taskId, String status, Duration timeout) {
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
    public @Nullable String awaitTaskStatus(String sessionId, String taskId, String status) {
        return awaitTaskStatus(sessionId, taskId, status, Duration.ofSeconds(5));
    }

    /** {@link #awaitTaskStatus(String, String, String, Duration)} against the stored session. */
    public @Nullable String awaitTaskStatus(String taskId, String status, Duration timeout) {
        return awaitTaskStatus(sessionId, taskId, status, timeout);
    }

    /** {@link #awaitTaskStatus(String, String, String)} against the stored session, 5s timeout. */
    public @Nullable String awaitTaskStatus(String taskId, String status) {
        return awaitTaskStatus(taskId, status, Duration.ofSeconds(5));
    }

    /** Closes the session with {@code DELETE}. */
    public HttpResponse<String> delete(String sessionId) throws Exception {
        return httpClient.send(
                baseRequest().header("MCP-Session-Id", sessionId).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Issues {@code DELETE} without a session header. */
    public HttpResponse<String> deleteWithoutSession() throws Exception {
        return httpClient.send(baseRequest().DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder baseRequest() {
        return HttpRequest.newBuilder()
                .uri(mcpEndpoint)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", protocolVersion());
    }

    private HttpRequest.Builder requestBuilder(String body) throws Exception {
        var builder = baseRequest();
        configureRequest(builder, body);
        return builder;
    }

    /** Hook: rewrites the request body before sending (e.g. 2026-07-28 {@code _meta} shaping). */
    protected String requestBody(String body) throws Exception {
        return body;
    }

    /** Hook: adds protocol-version-specific request headers (e.g. {@code Mcp-Method}). */
    protected void configureRequest(HttpRequest.Builder builder, String body) throws Exception {}

    /**
     * Parses {@code "id":<n>} or {@code "id":"<s>"} out of a JSON-RPC request body.
     */
    public static String extractRequestId(String requestBody) {
        var idx = requestBody.indexOf("\"id\"");
        if (idx < 0) return "";
        var colon = requestBody.indexOf(':', idx);
        if (colon < 0) return "";
        var sb = new StringBuilder();
        for (int i = colon + 1; i < requestBody.length(); i++) {
            var c = requestBody.charAt(i);
            if (c == ',' || c == '}') break;
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Walks all {@code data:} lines in an SSE body and returns the JSON payload whose JSON-RPC
     * envelope has an id matching {@code requestId}. Falls back to the last data line if no match.
     */
    public static String extractJsonRpcResponse(String sseBody, String requestId) {
        String last = null;
        var idMarker = "\"id\":" + requestId;
        for (var line : sseBody.split("\n")) {
            String data = null;
            if (line.startsWith("data: ")) data = line.substring("data: ".length());
            else if (line.startsWith("data:")) data = line.substring("data:".length());
            if (data == null) continue;
            last = data;
            if (!requestId.isEmpty() && data.contains(idMarker)) {
                return data;
            }
        }
        assertThat(last).as("SSE response must contain at least one data line").isNotNull();
        return last;
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
