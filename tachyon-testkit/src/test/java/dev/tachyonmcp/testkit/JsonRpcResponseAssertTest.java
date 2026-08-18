/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.TachyonServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThatJsonRpcResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link JsonRpcResponseAssert} against a real port-0 server and inline JSON-RPC envelopes.
 */
class JsonRpcResponseAssertTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static TachyonServer server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = McpTestServers.start(b -> b.session(c -> c.enabled(true)), s -> {
            s.tools()
                .register(
                    d -> d.name("echo").description("Echo back the input"),
                    (ctx, request) -> ToolResult.text(
                        "echo:" + request.arguments().stringOr("message", "")));
            s.tools()
                .register(
                    d -> d.name("boom").description("Always fails"),
                    (ctx, request) -> ToolResult.error("boom"));
            s.tools()
                .register(
                    d -> d.name("structured").description("Returns structured content"),
                    (ctx, request) -> ToolResult.structured(
                        JSON.createObjectNode().put("output", "success"), "done"));
        });
        port = server.port();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @Test
    void toolCallSuccessExposesTextContent() throws Exception {
        try (var client =
                 McpTestClients.builder(port).protocolVersion("2025-11-25").build()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}
                """);

            assertThat(response).isSuccess().hasContent().hasTextContent("echo:hi");
        }
    }

    @Test
    void toolExecutionFailureIsToolErrorNotJsonRpcError() throws Exception {
        try (var client =
                 McpTestClients.builder(port).protocolVersion("2025-11-25").build()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"boom","arguments":{}}}
                """);

            assertThat(response).isSuccess().isToolError();
        }
    }

    @Test
    void jsonRpcErrorForUnknownMethodExposesCodeAndMessage() throws Exception {
        try (var client =
                 McpTestClients.builder(port).protocolVersion("2025-11-25").build()) {
            var raw = client.sendRpc("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"missing","arguments":{}}}
                """);

            assertThatJsonRpcResponse(raw).isJsonRpcError().hasErrorCode(-32602).hasErrorMessageContaining("missing");
        }
    }

    @Test
    void structuredContentIsExposedAsJsonNode() throws Exception {
        try (var client =
                 McpTestClients.builder(port).protocolVersion("2025-11-25").build()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"structured","arguments":{}}}
                """);

            assertThat(response).hasStructuredContent(JSON.createObjectNode().put("output", "success"));
        }
    }

    @Test
    void isSuccessFailsOnAnErrorEnvelope() {
        var envelope = JSON.readTree("""
            {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid params"}}
            """);

        assertThatThrownBy(() -> assertThat(envelope).isSuccess()).isInstanceOf(AssertionError.class);
    }

    @Test
    void hasErrorCodeFailsOnASuccessEnvelope() {
        var envelope = JSON.readTree("""
            {"jsonrpc":"2.0","id":1,"result":{"content":[]}}
            """);

        assertThatThrownBy(() -> assertThat(envelope).hasErrorCode(-32602)).isInstanceOf(AssertionError.class);
    }

    @Test
    void hasErrorDataSatisfyingRunsTheGivenAssertionAgainstErrorData() {
        var envelope = JSON.readTree("""
            {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid params","data":{"field":"name"}}}
            """);

        assertThat(envelope)
            .hasErrorDataSatisfying(
                data -> assertThat(data.path("field").asString()).isEqualTo("name"));
    }
}
