/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolNotificationsTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.tool(EchoToolHandler.create()).tool(notifyingTool()));
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

    private static ToolHandler notifyingTool() {
        return ToolHandler.of(
                b -> b.name("notifier")
                        .title("Notifier Tool")
                        .description("Sends notifications and logs during execution"),
                (context, args) -> {
                    String text = "";
                    var msg = args.raw("message");
                    if (msg instanceof tools.jackson.databind.JsonNode node) {
                        text = node.asString();
                    }
                    context.notifications().send("notifications/tool/test", Map.of("message", text));
                    context.notifications().info("tool.notifier", Map.of("message", text));
                    return ToolResult.text(text);
                });
    }
}
