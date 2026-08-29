/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2025_11_25;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.config.CapabilitiesConfig;
import dev.tachyonmcp.e2e.mcp.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.e2e.mcp.EchoToolHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolNotificationsTest extends AbstractStatelessMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(
                b -> b.capabilities(CapabilitiesConfig.Builder::logging),
                s -> s.tools()
                        .registerAsync(EchoToolHandler.DESCRIPTOR, EchoToolHandler.FN)
                        .register(
                                ToolDescriptor.builder()
                                        .name("notifier")
                                        .title("Notifier Tool")
                                        .description("Sends notifications and logs during execution")
                                        .build(),
                                (context, request) -> {
                                    var args = request.arguments();
                                    var text = args.stringOr("message", "");
                                    context.notifications().progress(request.progressToken(), 1, 1, text);
                                    context.notifications().info("tool.notifier", Map.of("message", text));
                                    return ToolResult.text(text);
                                }));
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
}
