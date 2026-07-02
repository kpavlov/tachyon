/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class ToolErrorTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.tool(new ThrowingToolHandler()));
    }

    @Test
    void toolThrowsAfterSseUpgradeStillClosesStream() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var body = """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"boom","arguments":{}}}
                    """;
            var response = client.sendRequest(sessionId, body);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("notifications/before-boom");
            assertThat(response.body()).contains("-32603");
            assertThat(response.body()).contains("Internal error");
        }
    }

    private static class ThrowingToolHandler implements SyncToolHandler {

        @Override
        public String name() {
            return "boom";
        }

        @Override
        public String description() {
            return "Throws after sending a notification";
        }

        @Override
        public JsonNode inputSchema() {
            return JsonNodeFactory.instance.objectNode().put("type", "object");
        }

        @Override
        public ToolResult handle(McpContext context, ToolArgs arguments) {
            context.notifications().send("notifications/before-boom", Map.of());
            throw new RuntimeException("Simulated handler failure. Ignore it");
        }
    }
}
