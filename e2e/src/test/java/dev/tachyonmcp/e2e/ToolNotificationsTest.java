/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CallToolResult;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ContentBlock;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.TextContent;
import dev.tachyonmcp.server.features.tools.AbstractSyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.session.McpContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ToolNotificationsTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.tool(new EchoToolHandler()).tool(new NotifyingToolHandler()));
    }

    @Test
    void toolSendsNotificationAndLogEventInline() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var setLevelBody = """
                {"jsonrpc":"2.0","id":2,"method":"logging/setLevel","params":{"level":"info"}}
                """;
            client.sendRequest(sessionId, setLevelBody);

            var toolResponse = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"notifier","arguments":{"message":"hello from tool"}}}
                """);
            var body = toolResponse.body();

            assertThat(body).contains("notifications/tool/test");
            assertThat(body).contains("\"message\":\"hello from tool\"");

            assertThat(body).contains("notifications/message");
            assertThat(body).contains("\"level\":\"info\"");
            assertThat(body).contains("\"logger\":\"tool.notifier\"");

            assertThat(body).contains("\"result\"");
            assertThat(body).contains("hello from tool");
        }
    }

    private static class NotifyingToolHandler extends AbstractSyncToolHandler {

        NotifyingToolHandler() {
            super(ToolDescriptor.builder("notifier")
                    .title("Notifier Tool")
                    .description("Sends notifications and logs during execution")
                    .build());
        }

        @Override
        public Object handle(McpContext context, Object arguments) {
            var text = extractMessage(arguments);

            context.notifications().send("notifications/tool/test", Map.of("message", text));
            context.notifications().info("tool.notifier", Map.of("message", text));

            var textContent = TextContent.of(text);
            var content = List.<ContentBlock>of(textContent);
            return new CallToolResult(content, null, null, null, null);
        }

        private static String extractMessage(Object arguments) {
            if (arguments instanceof Map<?, ?> map) {
                var msg = map.get("message");
                if (msg instanceof JsonNode node) {
                    return node.asString();
                }
                if (msg instanceof String s) {
                    return s;
                }
            }
            return "";
        }
    }
}
