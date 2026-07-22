/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.tools.ToolHandler;
import org.junit.jupiter.api.Test;

class ToolErrorTest extends AbstractStatelessMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.tool(throwingTool()));
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
        startServer(it -> it.tool(ToolHandler.of("bad-arg", "Rejects input", (context, request) -> {
            throw new IllegalArgumentException("sensitive internal detail");
        })));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"bad-arg","arguments":{}}}
                """);
            // language=JSON
            var expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "error": {"code": -32602, "message": "Invalid params"}
                    }
                    """;
            assertThatJson(response).isEqualTo(expected);
            assertThat(response).doesNotContain("sensitive internal detail");
        }
    }

    private static ToolHandler throwingTool() {
        return ToolHandler.of(
                b -> b.name("boom").description("Throws after sending a notification"), (context, request) -> {
                    context.notifications().comment("before-boom");
                    throw new RuntimeException("Simulated handler failure. Ignore it");
                });
    }
}
