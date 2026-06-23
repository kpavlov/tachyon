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
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpSdkE2eTest extends AbstractMcpE2eTest {

    @Test
    void shouldConnectInitializeAndPing() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .build();
        var client = McpClient.sync(transport).build();

        var initResult = client.initialize();
        assertThat(initResult).isNotNull();
        assertThat(initResult.serverInfo().name()).isEqualTo("tachyon-mcp");

        var pingResult = client.ping();
        assertThat(pingResult).isNotNull();

        client.closeGracefully();
    }

    @Test
    void shouldListAndCallTool() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .build();
        var client = McpClient.sync(transport).build();
        client.initialize();

        var toolsResult = client.listTools();
        assertThat(toolsResult.tools()).hasSize(1);

        var tool = toolsResult.tools().getFirst();
        assertThat(tool.name()).isEqualTo("echo");
        assertThat(tool.description()).isEqualTo("Echo back the input message");

        var callRequest = McpSchema.CallToolRequest.builder("echo")
                .arguments(Map.of("message", "hello"))
                .build();
        var callResult = client.callTool(callRequest);
        assertThat(callResult.content()).hasSize(1);
        assertThat(callResult.content().getFirst()).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) callResult.content().getFirst()).text())
                .isEqualTo("hello");

        client.closeGracefully();
    }

    @Test
    void shouldEncodeServerCapabilitiesWithObjectNodeValues() throws Exception {
        try (var client = createTestClient()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}
                """);
            assertThat(response.statusCode()).isEqualTo(200);

            // language=JSON
            var expected = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "protocolVersion": "2025-11-25",
                    "capabilities": {
                      "logging": {},
                      "completions": {},
                      "tasks": { "list": {}, "cancel": {} },
                      "tools": { "listChanged": true }
                    },
                    "serverInfo": {
                      "version": "0.1",
                      "name": "tachyon-mcp"
                    }
                  }
                }
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void shouldEncodePromptMessageWithRoleEnum() throws Exception {
        server.prompts()
                .add(
                        PromptDescriptor.of("greeting", "A greeting prompt"),
                        List.of(PromptMessage.of(Role.USER, TextContent.of("Hello"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var getPromptResponse = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"greeting"}}
                """);
            assertThat(getPromptResponse.statusCode()).isEqualTo(200);

            // language=JSON
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
