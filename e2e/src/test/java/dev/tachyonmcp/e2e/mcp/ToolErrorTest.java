/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolErrorTest extends AbstractStatelessMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServerWith(s -> s.tools()
                .register(
                        b -> b.name("boom").description("Throws after sending a notification"), (context, request) -> {
                            context.notifications().comment("before-boom");
                            throw new RuntimeException("Simulated handler failure. Ignore it");
                        }));
    }

    @Test
    void toolThrowsAfterSseUpgradeStillClosesStream() throws Exception {
        try (var client = createTestClient()) {
            client.initialize();
            var body = """
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"boom","arguments":{}}}
                """;
            var response = client.post(body);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(": before-boom");
            // language=json
            assertThat(response.body()).contains("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32603,"message":"Tool handler failed"}}
                """.trim());
        }
    }

    @Test
    void shouldRedactIllegalArgumentExceptionFromInvalidParamsError() throws Exception {
        startServerWith(s -> s.tools()
                .register(builder -> builder.name("bad-arg").description("Rejects input"), (context, request) -> {
                    throw new IllegalArgumentException("sensitive internal detail");
                }));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"bad-arg","arguments":{}}}
                """);
            assertThatResponse(response)
                    .isJsonRpcError()
                    .hasId(2)
                    .hasErrorCode(-32602)
                    .hasErrorMessage("Invalid params");
            assertThat(response.body()).doesNotContain("sensitive internal detail");
        }
    }
}
