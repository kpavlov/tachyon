/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoggingTest extends AbstractMcpE2eTest {

    @Test
    void shouldReceiveLoggingNotificationAfterToolCall() throws Exception {
        try (var client = createTestClient()) {

            var sessionId = client.initialize();

            var setLevelBody = """
                {"jsonrpc":"2.0","id":2,"method":"logging/setLevel","params":{"level":"debug"}}
                """;
            var setLevelResponse = client.sendRequest(sessionId, setLevelBody);
            assertThatJson(setLevelResponse.body()).inPath("$.result").isObject();

            var toolBody = """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello"}}}
                """;
            var toolResponse = client.sendRequest(sessionId, toolBody);
            final String responseBody = toolResponse.body();

            assertThat(responseBody)
                    .contains(
                            // language=json
                            """
                {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"hello"}]}}
                """.trim());
        }
    }
}
