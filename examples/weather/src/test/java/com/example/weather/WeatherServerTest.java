/* Copyright (c) 2026 Konstantin Pavlov. */

package com.example.weather;

import dev.tachyonmcp.server.McpServerHandle;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherServerTest {

    private static McpServerHandle handle;
    private static int port;
    private static HttpClientStreamableHttpTransport clientTransport;
    private static McpSyncClient client;
    private static McpSchema.InitializeResult initResult;

    @BeforeAll
    static void beforeAll() {
        handle = WeatherServer.createServer(0);
        port = handle.port();

        clientTransport = HttpClientStreamableHttpTransport
            .builder("http://localhost:" + port)
            .build();
        client = McpClient.sync(clientTransport).build();

        initResult = client.initialize();
    }

    @AfterAll
    static void afterAll() {
        client.close();
        clientTransport.close();
        handle.close();
    }

    @Test
    void shouldServerInfo() {
        assertThat(initResult.serverInfo()).isEqualTo(
            McpSchema.Implementation.builder("weather-server", "1.0")
                .description("Weather MCP server")
                .build());
        assertThat(initResult.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(initResult.capabilities()).isEqualTo(McpSchema.ServerCapabilities.builder()
            .tools(false)
            .completions()
            .logging()
            .resources(false, false)
            .build()
        );
    }

    @Test
    void shouldListTools() {
        final var result = client.listTools();
        assertThat(result).isNotNull();
        assertThat(result.tools()).hasSize(1);
        McpSchema.Tool tool = result.tools().getFirst();
        assertThat(tool.name()).isEqualTo("get-weather");
        assertThat(tool.title()).isEqualTo("Current Weather");
        assertThat(tool.description()).isEqualTo("Get current weather for a city");
        assertThat(tool.inputSchema()).isEqualTo(Map.of(
            "type", "object",
            "required", List.of("city"),
            "properties", Map.of(
                "city", Map.of(
                    "description", "City name (e.g., London, Tokyo, New York)",
                    "type", "string"
                ),
                "units", Map.of(
                    "description", "Temperature unit (default: celsius)",
                    "enum", List.of("celsius", "fahrenheit"),
                    "type", "string"
                )
            )
        ));
        assertThat(tool.outputSchema()).isNull();
        assertThat(tool.meta()).isNull();

    }

    @Test
    void shouldCallWeatherTool() {
        final var result = client.callTool(McpSchema.CallToolRequest.builder("get-weather")
            .arguments(Map.of("city", "London",
                "units", "celsius"
            ))
            .build());

        assertThat(result).isNotNull();
        var content = result.content().getFirst();
        assertThat(content).isInstanceOf(McpSchema.TextContent.class);
        var textContent = ((McpSchema.TextContent) content);
        assertThat(textContent.text())
            .startsWith("Weather in London:")
            .contains("Temperature:")
            .contains("°C")
            .contains("Humidity:")
            .contains("Wind:");
    }

    @Test
    void shouldGetResource() {
        final var listResult = client.listResources();
        assertThat(listResult.resources()).hasSize(1);
        McpSchema.Resource resource = listResult.resources().getFirst();
        assertThat(resource.uri()).isEqualTo("weather://prediction/article");
        assertThat(resource.name()).isEqualTo("prediction-article");
        assertThat(resource.mimeType()).isEqualTo("text/markdown");

        final var resourceResult = client.readResource(resource);
        List<McpSchema.ResourceContents> contents = resourceResult.contents();
        assertThat(contents.getFirst()).isInstanceOf(McpSchema.TextResourceContents.class);
        var textContents = ((McpSchema.TextResourceContents) contents.getFirst());
        assertThat(textContents.uri()).isEqualTo("weather://prediction/article");
        assertThat(textContents.mimeType()).isEqualTo("text/markdown");
        assertThat(textContents.text().trim())
            .startsWith("# Weather Prediction")
            .endsWith("Published by Tachyon Weather MCP — " + LocalDate.now().format(DateTimeFormatter.ISO_DATE));
    }


}
