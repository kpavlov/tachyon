/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoggingE2eTest extends AbstractMcpE2eTest {

    @Test
    void shouldReceiveLoggingNotificationAfterToolCall() throws Exception {
        try (var client = createTestClient()) {

            var sessionId = client.initialize();

            var setLevelBody = """
                {"jsonrpc":"2.0","id":2,"method":"logging/setLevel","params":{"level":"info"}}
                """;
            var setLevelResponse = client.sendRequest(sessionId, setLevelBody);
            assertThatJson(setLevelResponse.body()).inPath("$.result").isObject();

            var toolBody = """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello"}}}
                """;
            var toolResponse = client.sendRequest(sessionId, toolBody);
            assertThat(toolResponse.body()).contains("notifications/message");
            assertThat(toolResponse.body()).contains("\"level\":\"info\"");
            assertThat(toolResponse.body()).contains("\"logger\":\"tachyon.tools\"");
            assertThat(toolResponse.body()).contains("\"tool\":\"echo\"");
        }
    }
}
