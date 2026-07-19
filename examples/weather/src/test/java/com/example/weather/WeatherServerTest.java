/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package com.example.weather;

import com.example.weather.service.WeatherService;
import dev.tachyonmcp.server.TachyonServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

class WeatherServerTest {

    private static TachyonServer handle;
    private static HttpClientStreamableHttpTransport clientTransport;
    private static McpSyncClient client;
    private static McpSchema.InitializeResult initResult;
    private static final List<McpSchema.ProgressNotification> progressNotifications = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void beforeAll() {
        handle = WeatherServer.createServer(0, new WeatherService(new TestWeatherProvider()));
        int port = handle.port();

        clientTransport = HttpClientStreamableHttpTransport
            .builder("http://localhost:" + port)
            .build();
        client = McpClient.sync(clientTransport)
            .elicitation(request -> new McpSchema.ElicitResult(
                McpSchema.ElicitResult.Action.ACCEPT, Map.of("city", "Tallinn")))
            .progressConsumer(progressNotifications::add)
            .build();

        initResult = client.initialize();
    }

    @BeforeEach
    void clearProgressNotifications() {
        progressNotifications.clear();
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
    void shouldGetServerInfo() {
        assertThat(initResult.serverInfo()).usingRecursiveComparison().ignoringFields("icons").isEqualTo(
            McpSchema.Implementation.builder("weather-server", "1.0")
                .title("Weather Server")
                .websiteUrl("https://github.com/kpavlov/tachyon/tree/main/examples/weather")
                .description("Weather MCP server")
                .build());
        assertThat(initResult.serverInfo().icons()).singleElement().satisfies(icon -> {
            assertThat(icon.src()).startsWith("data:image/png;base64,");
            assertThat(icon.mimeType()).isEqualTo("image/png");
            assertThat(icon.sizes()).containsExactly("256x256");
        });
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
        assertThat(tool.inputSchema()).isEqualTo(Map.of(
            "type", "object",
            "required", List.of("city"),
            "properties", Map.of(
                "city", Map.of(
                    "description", "City name (e.g., London, Tokyo, New York)",
                    "type", "string"),
                "units", Map.of(
                    "description", "Temperature unit (default: celsius)",
                    "enum", List.of("celsius", "fahrenheit"),
                    "type", "string"))));
        assertThat(tool.outputSchema()).isNull();
        assertThat(tool.meta()).isNull();
    }

    @Test
    void shouldCallWeatherTool() {
        final var result = client.callTool(McpSchema.CallToolRequest.builder("get-weather")
            .arguments(Map.of("city", "London", "units", "celsius"))
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
    void shouldEmitProgressWhileFetchingWeather() {
        final var result = client.callTool(McpSchema.CallToolRequest.builder("get-weather")
            .arguments(Map.of("city", "London"))
            .progressToken("weather-progress")
            .build());

        assertThat(result.isError()).isNotEqualTo(true);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(progressNotifications).hasSize(2));
        assertThat(progressNotifications)
            .extracting(
                McpSchema.ProgressNotification::progressToken,
                McpSchema.ProgressNotification::progress,
                McpSchema.ProgressNotification::total,
                McpSchema.ProgressNotification::message)
            .containsExactly(
                tuple("weather-progress", 0.1, 1.0, "Fetching weather for London"),
                tuple("weather-progress", 1.0, 1.0, "Weather retrieved for London"));
    }

    @Test
    void shouldCallWeatherToolAfterElicitingAnotherCity() {
        final var result = client.callTool(McpSchema.CallToolRequest.builder("get-weather")
            .arguments(Map.of("city", "Unknown"))
            .build());

        var content = result.content().getFirst();
        assertThat(content).isInstanceOf(McpSchema.TextContent.class);
        assertThat(((McpSchema.TextContent) content).text()).startsWith("Weather in Tallinn:");
    }

    @Test
    void shouldListResources() {
        final var result = client.listResources();

        assertThat(result.resources()).hasSize(2);

        var article = result.resources().stream()
            .filter(resource -> resource.uri().equals("weather://prediction/article"))
            .findFirst().orElseThrow();
        assertThat(article.uri()).isEqualTo("weather://prediction/article");
        assertThat(article.name()).isEqualTo("prediction-article");
        assertThat(article.mimeType()).isEqualTo("text/markdown");

        var weather = result.resources().stream()
            .filter(resource -> resource.uri().equals("weather://featured/current"))
            .findFirst().orElseThrow();
        assertThat(weather.uri()).isEqualTo("weather://featured/current");
        assertThat(weather.name()).isEqualTo("featured-current-weather");
        assertThat(weather.mimeType()).isEqualTo("application/json");
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
            .endsWith("reports, ocean buoys, and over 30 polar-orbiting and geostationary satellites.");
    }

    @Test
    void shouldReadCurrentWeatherResource() {
        final var listResult = client.listResources();
        var weather = listResult.resources().stream()
            .filter(r -> r.uri().equals("weather://featured/current"))
            .findFirst().orElseThrow();

        final var result = client.readResource(weather);

        var contents = result.contents().getFirst();
        assertThat(contents).isInstanceOf(McpSchema.TextResourceContents.class);
        var textContents = ((McpSchema.TextResourceContents) contents);
        assertThat(textContents.uri()).isEqualTo("weather://featured/current");
        assertThat(textContents.mimeType()).isEqualTo("application/json");
        assertThat(textContents.text()).contains("Tallinn", "Clear sky");
    }

    @Test
    void shouldListResourceTemplates() {
        final var result = client.listResourceTemplates();

        assertThat(result.resourceTemplates()).hasSize(1);
        var template = result.resourceTemplates().getFirst();
        assertThat(template.uriTemplate()).isEqualTo("weather://current/{city}");
        assertThat(template.name()).isEqualTo("current-weather");
        assertThat(template.mimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldReadCurrentWeatherFromTemplate() {
        final var result = client.readResource(
            McpSchema.ReadResourceRequest.builder("weather://current/London").build());

        var contents = result.contents().getFirst();
        assertThat(contents).isInstanceOf(McpSchema.TextResourceContents.class);
        var textContents = ((McpSchema.TextResourceContents) contents);
        assertThat(textContents.uri()).isEqualTo("weather://current/London");
        assertThat(textContents.mimeType()).isEqualTo("application/json");
        assertThat(textContents.text()).contains("London");
    }

    @Test
    void shouldReturnInvalidParamsWhenTemplateCityIsUnknown() {
        assertThatThrownBy(() -> client.readResource(
                McpSchema.ReadResourceRequest.builder("weather://current/Unknown").build()))
            .isInstanceOf(McpError.class)
            .extracting(e -> ((McpError) e).getJsonRpcError().code())
            .isEqualTo(-32602);
    }

    @Test
    void shouldListPrompts() {
        final var result = client.listPrompts();

        assertThat(result.prompts()).hasSize(1);
        var prompt = result.prompts().getFirst();
        assertThat(prompt.name()).isEqualTo("rewrite-forecast");
        assertThat(prompt.description()).isEqualTo("Rewrites a weather forecast in a chosen style");
        assertThat(prompt.arguments()).hasSize(2);
    }

    @Test
    void shouldGetPrompt() {
        final var result = client.getPrompt(
            McpSchema.GetPromptRequest.builder("rewrite-forecast")
                .arguments(Map.of("forecast", "Rain in London", "style", "pirate"))
                .build());

        assertThat(result).isNotNull();
        assertThat(result.messages()).hasSize(1);
        var message = result.messages().getFirst();
        assertThat(message.role()).isEqualTo(McpSchema.Role.USER);
        assertThat(message.content()).isInstanceOf(McpSchema.TextContent.class);
        var textContent = ((McpSchema.TextContent) message.content());
        assertThat(textContent.text())
            .isEqualTo("Rewrite the following weather forecast in pirate style. Preserve factual details:\n\n```Rain in London\n```");
    }
}
