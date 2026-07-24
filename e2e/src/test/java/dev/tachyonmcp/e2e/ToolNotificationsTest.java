/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.config.CapabilitiesConfig;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolNotificationsTest extends AbstractStatelessMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.capabilities(CapabilitiesConfig.Builder::logging)
                .tool(EchoToolHandler.create())
                .tool(notifyingTool()));
    }

    @Test
    void toolSendsNotificationAndLogEventInline() throws Exception {
        try (var client = createTestClient()) {
            client.initialize();
            var setLevelBody = """
                {"jsonrpc":"2.0","id":2,"method":"logging/setLevel","params":{"level":"info"}}
                """;
            client.post(setLevelBody);

            var toolResponse = client.post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"notifier","arguments":{"message":"hello from tool"},
                           "_meta":{"progressToken":"pt-1"}}}
                """);
            var body = toolResponse.body();

            assertThat(body).contains("notifications/progress");
            assertThat(body).contains("\"message\":\"hello from tool\"");

            assertThat(body).contains("notifications/message");
            assertThat(body).contains("\"level\":\"info\"");
            assertThat(body).contains("\"logger\":\"tool.notifier\"");

            assertThat(body).contains("\"result\"");
            assertThat(body).contains("hello from tool");
        }
    }

    private static ToolHandler notifyingTool() {
        var descriptor = ToolDescriptor.builder()
                .name("notifier")
                .title("Notifier Tool")
                .description("Sends notifications and logs during execution")
                .build();
        return ToolHandler.of(descriptor, (context, request) -> {
            var args = request.arguments();
            var text = args.stringOr("message", "");
            context.notifications().progress(request.progressToken(), 1, 1, text);
            context.notifications().info("tool.notifier", Map.of("message", text));
            return ToolResult.text(text);
        });
    }
}
