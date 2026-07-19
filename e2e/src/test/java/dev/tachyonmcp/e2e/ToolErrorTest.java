/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

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

    private static ToolHandler throwingTool() {
        return ToolHandler.of(
                b -> b.name("boom").description("Throws after sending a notification"), (context, args) -> {
                    context.notifications().comment("before-boom");
                    throw new RuntimeException("Simulated handler failure. Ignore it");
                });
    }
}
