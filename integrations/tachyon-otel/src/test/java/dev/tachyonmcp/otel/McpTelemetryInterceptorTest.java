/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.otel;

import static dev.tachyonmcp.otel.McpAttributes.EXECUTE_TOOL;
import static dev.tachyonmcp.otel.McpAttributes.GEN_AI_OPERATION_NAME;
import static dev.tachyonmcp.otel.McpAttributes.GEN_AI_TOOL_CALL_ARGUMENTS;
import static dev.tachyonmcp.otel.McpAttributes.GEN_AI_TOOL_NAME;
import static dev.tachyonmcp.otel.McpAttributes.MCP_METHOD_NAME;
import static dev.tachyonmcp.otel.McpAttributes.MCP_PROTOCOL_VERSION;
import static dev.tachyonmcp.otel.McpAttributes.MCP_RESOURCE_URI;
import static dev.tachyonmcp.otel.McpAttributes.MCP_SESSION_ID;
import static dev.tachyonmcp.otel.McpAttributes.TOOL_ERROR;
import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.testkit.Mcp20251125Client;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.McpClient;
import dev.tachyonmcp.testkit.McpTestClients;
import dev.tachyonmcp.testkit.McpTestServers;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.NetworkAttributes;
import java.io.IOException;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * E2E: a real Tachyon server with {@link McpTelemetryInterceptor} registered, driven over HTTP by
 * the testkit client, asserting the spans and metrics an OpenTelemetry backend would receive.
 *
 * <p>Conventions under test: <a
 * href="https://github.com/open-telemetry/semantic-conventions-genai/tree/main/model/mcp">
 * semantic-conventions-genai / model / mcp</a>.
 */
class McpTelemetryInterceptorTest {

    private static final String HANDLER_SPAN = "handler-work";

    private InMemorySpanExporter spans;
    private InMemoryMetricReader metrics;
    private OpenTelemetrySdk otel;

    @BeforeEach
    void setUp() {
        spans = InMemorySpanExporter.create();
        metrics = InMemoryMetricReader.create();
        otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spans))
                        .build())
                .setMeterProvider(
                        SdkMeterProvider.builder().registerMetricReader(metrics).build())
                .build();
    }

    @AfterEach
    void tearDown() {
        otel.close();
    }

    /**
     * The regression test for the whole outcome design: the two protocol versions encode {@code
     * RESOURCE_NOT_FOUND} differently, so a status code the interceptor derived on its own would be
     * wrong on one of them.
     */
    @ParameterizedTest(name = "{0} encodes an unknown resource as {1}")
    @CsvSource({Mcp20251125Client.PROTOCOL_VERSION + ",-32002", Mcp20260728Client.PROTOCOL_VERSION + ",-32602"})
    @DisplayName("rpc.response.status_code comes from the protocol codec, not from the interceptor")
    void statusCodeIsResolvedPerProtocolVersion(String protocolVersion, String expectedCode) throws Exception {
        try (var server = startServer(telemetry());
                var client = McpTestClients.forVersion(server.port(), protocolVersion)) {
            var sessionId = initialized(client);

            var response = client.post(
                    sessionId,
                    // language=json
                    """
                    {"jsonrpc":"2.0","id":20,"method":"resources/read","params":{"uri":"resource://absent"}}""");
            assertThat(response).isJsonRpcError();

            var span = spanFor("resources/read");
            assertThat(span.getAttributes().asMap())
                    .containsEntry(RPC_RESPONSE_STATUS_CODE, expectedCode)
                    .containsEntry(ERROR_TYPE, "RESOURCE_NOT_FOUND")
                    .containsEntry(MCP_PROTOCOL_VERSION, protocolVersion);
            // the caller asked for something that isn't there — not a server fault
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        }
    }

    @Test
    @DisplayName("tools/call produces a SERVER span '{method} {tool}' with GenAI attributes and no payload")
    void toolCallSpan() throws Exception {
        withServer(telemetry(), client -> {
            var sessionId = initialized(client);
            var response = client.post(sessionId, callForecast());

            assertThat(response).isSuccess().hasTextContent("sunny");

            var span = spanFor("tools/call forecast");
            assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            assertThat(span.getAttributes().asMap())
                    .containsEntry(MCP_METHOD_NAME, "tools/call")
                    .containsEntry(GEN_AI_TOOL_NAME, "forecast")
                    .containsEntry(GEN_AI_OPERATION_NAME, EXECUTE_TOOL)
                    .containsEntry(MCP_PROTOCOL_VERSION, Mcp20251125Client.PROTOCOL_VERSION)
                    .containsEntry(MCP_SESSION_ID, sessionId)
                    .containsEntry(JSONRPC_REQUEST_ID, "10")
                    .containsEntry(NETWORK_TRANSPORT, NetworkAttributes.NetworkTransportValues.TCP)
                    .containsEntry(NETWORK_PROTOCOL_NAME, "http")
                    .doesNotContainKey(ERROR_TYPE)
                    .doesNotContainKey(RPC_RESPONSE_STATUS_CODE)
                    // opt_in per the conventions: arguments carry credentials and personal data
                    .doesNotContainKey(GEN_AI_TOOL_CALL_ARGUMENTS);
        });
    }

    @Test
    @DisplayName("recordPayloads(true) records tool arguments as JSON")
    void payloadsWhenOptedIn() throws Exception {
        withServer(telemetryRecordingPayloads(), client -> {
            var sessionId = initialized(client);
            client.post(sessionId, callForecast());

            assertThat(spanFor("tools/call forecast").getAttributes().get(GEN_AI_TOOL_CALL_ARGUMENTS))
                    .contains("\"city\"")
                    .contains("Berlin");
        });
    }

    @Test
    @DisplayName("a tool returning ToolResult.error is error.type=tool_error on a successful span")
    void toolErrorIsClassified() throws Exception {
        withServer(telemetry(), client -> {
            var sessionId = initialized(client);
            var response = client.post(
                    sessionId,
                    // language=json
                    """
                    {"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"failing","arguments":{}}}""");

            assertThat(response).isSuccess().isToolError();

            // A payload failure is still a JSON-RPC success: no status code, span not failed.
            var span = spanFor("tools/call failing");
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            assertThat(span.getAttributes().asMap())
                    .containsEntry(ERROR_TYPE, TOOL_ERROR)
                    .doesNotContainKey(RPC_RESPONSE_STATUS_CODE);
        });
    }

    @Test
    @DisplayName("a throwing tool fails the span: INTERNAL_ERROR, JSON-RPC -32603, status ERROR")
    void throwingToolFailsSpan() throws Exception {
        withServer(telemetry(), client -> {
            var sessionId = initialized(client);
            var response = client.post(
                    sessionId,
                    // language=json
                    """
                    {"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"throwing","arguments":{}}}""");

            assertThat(response).isJsonRpcError().hasErrorCode(-32603);

            var span = spanFor("tools/call throwing");
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getAttributes().asMap())
                    .containsEntry(ERROR_TYPE, "INTERNAL_ERROR")
                    .containsEntry(RPC_RESPONSE_STATUS_CODE, "-32603");
        });
    }

    @Test
    @DisplayName("an unknown tool records the JSON-RPC code but leaves the span UNSET — the caller's fault")
    void callerFaultIsNotAServerFault() throws Exception {
        withServer(telemetry(), client -> {
            var sessionId = initialized(client);
            var response = client.post(
                    sessionId,
                    // language=json
                    """
                    {"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"absent","arguments":{}}}""");

            assertThat(response).isJsonRpcError().hasErrorCode(-32602);

            var span = spanFor("tools/call absent");
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
            assertThat(span.getAttributes().asMap())
                    .containsEntry(RPC_RESPONSE_STATUS_CODE, "-32602")
                    .containsEntry(ERROR_TYPE, "INVALID_PARAMS");
        });
    }

    @Test
    @DisplayName("resources/read carries mcp.resource.uri but keeps it out of the span name")
    void resourceUriAttribute() throws Exception {
        withServer(telemetry(), client -> {
            var sessionId = initialized(client);
            var response = client.post(
                    sessionId,
                    // language=json
                    """
                    {"jsonrpc":"2.0","id":14,"method":"resources/read","params":{"uri":"resource://greeting"}}""");

            assertThat(response).isSuccess();

            assertThat(spanFor("resources/read").getAttributes().get(MCP_RESOURCE_URI))
                    .isEqualTo("resource://greeting");
        });
    }

    @Test
    @DisplayName("notifications are traced, without a jsonrpc.request.id")
    void notificationSpan() throws Exception {
        withServer(telemetry(), client -> {
            initialized(client);

            var span = awaitSpanFor("notifications/initialized");
            assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
            assertThat(span.getAttributes().asMap())
                    .containsEntry(MCP_METHOD_NAME, "notifications/initialized")
                    .doesNotContainKey(JSONRPC_REQUEST_ID);
        });
    }

    @Test
    @DisplayName("initialize is intercepted too; the duration histogram omits high-cardinality ids")
    void durationHistogramCoversEveryOperation() throws Exception {
        withServer(telemetry(), client -> {
            var sessionId = initialized(client);
            client.post(sessionId, callForecast());
            awaitSpanFor("notifications/initialized");

            assertThat(spans.getFinishedSpanItems())
                    .extracting(SpanData::getName)
                    .contains("initialize", "notifications/initialized", "tools/call forecast");

            var histogram = metrics.collectAllMetrics().stream()
                    .filter(metric -> "mcp.server.operation.duration".equals(metric.getName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(histogram.getUnit()).isEqualTo("s");
            assertThat(histogram.getHistogramData().getPoints())
                    .hasSizeGreaterThanOrEqualTo(3)
                    .allSatisfy(point -> assertThat(point.getAttributes().asMap())
                            .containsKey(MCP_METHOD_NAME)
                            .containsKey(MCP_PROTOCOL_VERSION)
                            .doesNotContainKey(MCP_SESSION_ID)
                            .doesNotContainKey(JSONRPC_REQUEST_ID));
        });
    }

    private McpTelemetryInterceptor telemetry() {
        return McpTelemetryInterceptor.create(otel);
    }

    private McpTelemetryInterceptor telemetryRecordingPayloads() {
        return McpTelemetryInterceptor.builder(otel).recordPayloads(true).build();
    }

    /**
     * Completes the handshake and returns the session id, or {@code null} on 2026-07-28 — that
     * revision removed {@code initialize} and sessions; every request self-describes instead.
     *
     * <p>{@link McpClient#initialize()} already sends {@code notifications/initialized}; sending it
     * again here would double every notification span.
     */
    private static @Nullable String initialized(McpClient client) throws Exception {
        return client instanceof Mcp20260728Client ? null : client.initialize();
    }

    /** Notifications are dispatched off the request thread — the 202 returns before the chain ends. */
    private SpanData awaitSpanFor(String name) {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .extracting(SpanData::getName)
                        .contains(name));
        return spanFor(name);
    }

    private SpanData spanFor(String name) {
        var matching = spans.getFinishedSpanItems().stream()
                .filter(span -> name.equals(span.getName()))
                .toList();
        assertThat(matching).as("spans named '%s'", name).hasSize(1);
        return matching.getFirst();
    }

    /**
     * Locks the invariant that the interceptor's span scope encloses the handler, so a span the
     * handler starts joins the trace instead of becoming a root. A synchronous chain gets this for
     * free — chain and handler share the thread — which is why {@code makeCurrent()} must stay
     * wrapped around {@code chain.proceed()} and never be narrowed to the span construction.
     */
    @Test
    @DisplayName("a span started inside a handler is a child of the interceptor's span")
    void handlerSpanIsChildOfTheInterceptorSpan() throws Exception {
        withServer(telemetry(), client -> {
            var sessionId = initialized(client);

            assertThat(client.post(
                            sessionId,
                            // language=json
                            """
                            {"jsonrpc":"2.0","id":30,"method":"tools/call","params":{"name":"nested","arguments":{}}}"""))
                    .isSuccess();

            var serverSpan = spanFor("tools/call nested");
            var handlerSpan = spanFor(HANDLER_SPAN);
            assertThat(handlerSpan.getParentSpanId()).isEqualTo(serverSpan.getSpanId());
            assertThat(handlerSpan.getTraceId()).isEqualTo(serverSpan.getTraceId());
        });
    }

    /** {@code id} 10 backs the {@code jsonrpc.request.id} assertion in {@link #toolCallSpan()}. */
    private static String callForecast() {
        // language=json
        return """
                {"jsonrpc":"2.0","id":10,"method":"tools/call",\
                "params":{"name":"forecast","arguments":{"city":"Berlin"}}}""";
    }

    private void withServer(McpTelemetryInterceptor interceptor, ClientScenario scenario) throws Exception {
        try (var server = startServer(interceptor);
                var client = new Mcp20251125Client(server.port())) {
            scenario.run(client);
        }
    }

    private TachyonServer startServer(McpTelemetryInterceptor interceptor) {
        return McpTestServers.start(
                builder -> builder.withInterceptors(interceptor).session(session -> session.enabled(true)), server -> {
                    server.tools().register(tool -> tool.name("forecast"), (ctx, request) -> ToolResult.text("sunny"));
                    server.tools().register(tool -> tool.name("failing"), (ctx, request) -> ToolResult.error("nope"));
                    server.tools().register(tool -> tool.name("throwing"), (ctx, request) -> {
                        throw new IOException("boom");
                    });
                    server.tools().register(tool -> tool.name("nested"), (ctx, request) -> {
                        // the handler's own instrumentation, parented by whatever is current
                        var inner = otel.getTracer("handler")
                                .spanBuilder(HANDLER_SPAN)
                                .startSpan();
                        inner.end();
                        return ToolResult.text("nested");
                    });
                    server.resources()
                            .register(
                                    resource -> resource.name("greeting").uri("resource://greeting"),
                                    (ctx, request) -> TextResourceContents.of(request.uri(), "hello", "text/plain"));
                });
    }

    @FunctionalInterface
    private interface ClientScenario {
        void run(Mcp20251125Client client) throws Exception;
    }
}
