/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package com.example.mcpjava;

import dev.tachyonmcp.core.server.TachyonServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpJavaServerTest {

    private static TachyonServer server;
    private static McpSyncClient client;

    @BeforeAll
    static void beforeAll() {
        server = McpJavaServer.buildServer("localhost", 0, null);
        server.start();
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + server.port()).build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterAll
    static void afterAll() {
        client.close();
        server.close();
    }

    @Test
    void registersAllMcpJavaAnnotations() {
        assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).contains("add");
        assertThat(client.listResources().resources()).extracting(McpSchema.Resource::name).contains("config");
        assertThat(client.listResourceTemplates().resourceTemplates())
            .extracting(McpSchema.ResourceTemplate::name)
            .contains("greeting");
        assertThat(client.listPrompts().prompts()).extracting(McpSchema.Prompt::name).contains("welcome");
    }

    @Test
    void invokesAnnotatedTool() {
        var result = client.callTool(McpSchema.CallToolRequest.builder("add")
            .arguments(Map.of("left", 2, "right", 3))
            .build());

        assertThat(result.isError()).isNotEqualTo(true);
        assertThat(result.content()).singleElement().isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).isEqualTo("5");
    }

    @Test
    void readsAnnotatedResourceAndPrompt() {
        var resource = client.readResource(
            McpSchema.ReadResourceRequest
                .builder("app://greeting/Ada")
                .build()
        );
        assertThat(resource.contents()).singleElement().isInstanceOf(McpSchema.TextResourceContents.class);
        assertThat(((McpSchema.TextResourceContents) resource.contents().getFirst()).text())
            .isEqualTo("Hello, Ada!");

        var prompt = client.getPrompt(
            McpSchema.GetPromptRequest
                .builder("welcome")
                .arguments(Map.of("name", "Ada"))
                .build()
        );
        assertThat(prompt.messages()).singleElement().extracting(McpSchema.PromptMessage::content)
            .isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) prompt.messages().getFirst().content()).text())
            .isEqualTo("Welcome to MCP, Ada!");
    }
}
