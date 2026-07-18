/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatefulMcpSdkTest extends AbstractStatefulMcpE2eTest implements McpSdkContract {

    @Override
    public int port() {
        return port;
    }

    @Test
    void shouldEncodePromptMessageWithRoleEnum() throws Exception {
        startEmptyServer();
        server.prompts()
                .register(
                        PromptDescriptor.of("role-enum-prompt", "A greeting prompt"),
                        List.of(PromptMessage.of(Role.USER, TextContent.of("Hello"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var getPromptResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"role-enum-prompt"}}
                """);
            assertThat(getPromptResponse.statusCode()).isEqualTo(200);

            var expected = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "result": {
                    "description": "A greeting prompt",
                    "messages": [
                      {
                        "role": "user",
                        "content": {
                          "type": "text",
                          "text": "Hello"
                        }
                      }
                    ]
                  }
                }
                """;
            assertThatJson(getPromptResponse.body()).isEqualTo(expected);
        }
    }
}
