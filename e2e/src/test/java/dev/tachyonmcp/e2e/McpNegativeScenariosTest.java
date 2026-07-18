/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(Lifecycle.PER_CLASS)
class McpNegativeScenariosTest extends AbstractStatelessMcpE2eTest {

    @Test
    void shouldRejectUnknownTool() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .build();
        try (var client = McpClient.sync(transport).build()) {
            client.initialize();

            var callRequest = CallToolRequest.builder("nonexistent-tool")
                    .arguments(Map.of())
                    .build();
            try {
                var result = client.callTool(callRequest);
                assertThat(result.isError()).isTrue();
            } catch (Exception e) {
                // expected — SDK may throw for unknown tool
            }
        }
    }

    @Test
    void shouldReturnDefaultToolsOnList() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .build();
        try (var client = McpClient.sync(transport).build()) {
            client.initialize();

            var toolsResult = client.listTools();
            assertThat(toolsResult.tools()).isNotEmpty();
        }
    }

    @Test
    void shouldHandlePingSuccessfully() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .build();
        try (var client = McpClient.sync(transport).build()) {
            client.initialize();

            var result = client.ping();
            assertThat(result).isNotNull();
        }
    }

    @Test
    void shouldListRegisteredPromptViaSdk() {
        startEmptyServer();
        var promptName = "negative-scenarios-list-probe";
        server.prompts()
                .register(PromptDescriptor.of(promptName, "A probe prompt"), List.of(PromptMessage.user("Hello!")));

        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .build();
        try (var client = McpClient.sync(transport).build()) {
            client.initialize();

            var result = client.listPrompts();
            assertThat(result).isNotNull();
            assertThat(result.prompts()).anyMatch(p -> promptName.equals(p.name()));
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"listResources", "listResourceTemplates"})
    void shouldThrowWhenResourceCapabilityMissing(String method) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .build();
        try (var client = McpClient.sync(transport).build()) {
            client.initialize();

            assertThatThrownBy(() -> {
                        if ("listResources".equals(method)) {
                            client.listResources();
                        } else {
                            client.listResourceTemplates();
                        }
                    })
                    .isInstanceOf(Exception.class);
        }
    }
}
