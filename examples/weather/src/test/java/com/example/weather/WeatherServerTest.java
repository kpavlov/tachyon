/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package com.example.weather;

import dev.tachyonmcp.server.TachyonServer;
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

import static org.assertj.core.api.Assertions.assertThat;

class WeatherServerTest {

    private static TachyonServer handle;
    private static HttpClientStreamableHttpTransport clientTransport;
    private static McpSyncClient client;
    private static McpSchema.InitializeResult initResult;

    @BeforeAll
    static void beforeAll() {
        handle = WeatherServer.createServer(0);
        int port = handle.port();

        clientTransport = HttpClientStreamableHttpTransport
            .builder("http://localhost:" + port)
            .build();
        client = McpClient.sync(clientTransport).build();

        initResult = client.initialize();
    }

    @AfterAll
    static void afterAll() {
        if (client != null) {
            client.close();
        }
        if (clientTransport != null) {
            clientTransport.close();
        }
        if (handle != null) {
            handle.close();
        }
    }

    @Test
    void shouldServerInfo() {
        assertThat(initResult.serverInfo()).isEqualTo(
            McpSchema.Implementation.builder("weather-server", "1.0")
                .title("Weather Server")
                .websiteUrl("http://localhost:8080/mcp")
                .description("Weather MCP server")
                .build());
        assertThat(initResult.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(initResult.instructions()).isEqualTo("Test instructions");
        assertThat(initResult.capabilities()).isEqualTo(McpSchema.ServerCapabilities.builder()
            .tools(null)
            .resources(null, null)
            .prompts(null)
            .build());
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
        assertThat(tool.inputSchema()).isEqualTo(java.util.Map.of(
            "type", "object",
            "required", List.of("city"),
            "properties", java.util.Map.of(
                "city", java.util.Map.of(
                    "description", "City name (e.g., London, Tokyo, New York)",
                    "type", "string"),
                "units", java.util.Map.of(
                    "description", "Temperature unit (default: celsius)",
                    "enum", List.of("celsius", "fahrenheit"),
                    "type", "string"))));
        assertThat(tool.outputSchema()).isNull();
        assertThat(tool.meta()).isNull();
    }

    @Test
    void shouldCallWeatherTool() {
        final var result = client.callTool(McpSchema.CallToolRequest.builder("get-weather")
            .arguments(java.util.Map.of("city", "London", "units", "celsius"))
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
    void shouldListResources() {
        final var result = client.listResources();

        assertThat(result.resources()).hasSize(2);

        var article = result.resources().get(0);
        assertThat(article.uri()).isEqualTo("weather://prediction/article");
        assertThat(article.name()).isEqualTo("prediction-article");
        assertThat(article.mimeType()).isEqualTo("text/markdown");

        var image = result.resources().get(1);
        assertThat(image.uri()).isEqualTo("weather://current/image");
        assertThat(image.name()).isEqualTo("weather-image");
        assertThat(image.mimeType()).isEqualTo("image/png");
    }

    @Test
    void shouldReadTextResource() {
        final var listResult = client.listResources();
        var article = listResult.resources().stream()
            .filter(r -> r.uri().equals("weather://prediction/article"))
            .findFirst().orElseThrow();

        final var result = client.readResource(article);

        var contents = result.contents().getFirst();
        assertThat(contents).isInstanceOf(McpSchema.TextResourceContents.class);
        var textContents = ((McpSchema.TextResourceContents) contents);
        assertThat(textContents.uri()).isEqualTo("weather://prediction/article");
        assertThat(textContents.mimeType()).isEqualTo("text/markdown");
        assertThat(textContents.text().trim())
            .startsWith("# Weather Prediction")
            .endsWith("Published by Tachyon Weather MCP — "
                + LocalDate.now().format(DateTimeFormatter.ISO_DATE));
    }

    @Test
    void shouldReadBlobResource() {
        final var listResult = client.listResources();
        var image = listResult.resources().stream()
            .filter(r -> r.uri().equals("weather://current/image"))
            .findFirst().orElseThrow();

        final var result = client.readResource(image);

        var contents = result.contents().getFirst();
        assertThat(contents).isInstanceOf(McpSchema.BlobResourceContents.class);
        var blobContents = ((McpSchema.BlobResourceContents) contents);
        assertThat(blobContents.uri()).isEqualTo("weather://current/image");
        assertThat(blobContents.mimeType()).isEqualTo("image/png");
        assertThat(blobContents.blob()).isNotBlank();
    }

    @Test
    void shouldListResourceTemplates() {
        final var result = client.listResourceTemplates();

        assertThat(result.resourceTemplates()).hasSize(1);
        var template = result.resourceTemplates().getFirst();
        assertThat(template.uriTemplate()).isEqualTo("weather://forecast/{city}");
        assertThat(template.name()).isEqualTo("forecast");
        assertThat(template.mimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldReadForecastFromTemplate() {
        final var result = client.readResource(
            McpSchema.ReadResourceRequest.builder("weather://forecast/London").build());

        var contents = result.contents().getFirst();
        assertThat(contents).isInstanceOf(McpSchema.TextResourceContents.class);
        var textContents = ((McpSchema.TextResourceContents) contents);
        assertThat(textContents.uri()).isEqualTo("weather://forecast/London");
        assertThat(textContents.mimeType()).isEqualTo("application/json");
        assertThat(textContents.text()).contains("London");
    }

    @Test
    void shouldListPrompts() {
        final var result = client.listPrompts();

        assertThat(result.prompts()).hasSize(1);
        var prompt = result.prompts().getFirst();
        assertThat(prompt.name()).isEqualTo("rewrite-forecast");
        assertThat(prompt.description()).isEqualTo("Rewrites a weather forecast in a given style");
    }

    @Test
    void shouldGetPrompt() {
        final var result = client.getPrompt(
            McpSchema.GetPromptRequest.builder("rewrite-forecast").build());

        assertThat(result).isNotNull();
        assertThat(result.messages()).hasSize(1);
        var message = result.messages().getFirst();
        assertThat(message.role()).isEqualTo(McpSchema.Role.USER);
        assertThat(message.content()).isInstanceOf(McpSchema.TextContent.class);
        var textContent = ((McpSchema.TextContent) message.content());
        assertThat(textContent.text()).isEqualTo("Rewrite this forecast in a pirate style.");
    }
}
