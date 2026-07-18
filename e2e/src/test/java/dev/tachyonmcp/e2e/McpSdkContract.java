/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;

interface McpSdkContract {

    int port();

    @Test
    default void shouldConnectInitializeAndPing() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port())
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
    default void shouldListAndCallTool() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port())
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
}
