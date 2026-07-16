/* Copyright (c) 2026 Konstantin Pavlov. */

package com.example.weather;

import dev.tachyonmcp.server.McpServerHandle;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.*;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.IntNode;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class WeatherServer {

    private static final Logger log = LoggerFactory.getLogger(WeatherServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RandomGenerator RNG = RandomGenerator.getDefault();
    private static final String PNG_BASE64;

    static {
        try {
            var bytes = WeatherServer.class.getClassLoader().getResourceAsStream("images/sun-and-cloud.png").readAllBytes();
            PNG_BASE64 = Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void main() {
        final var handle = createServer(8080);
        final var port = handle.port();
        log.info("Weather MCP server listening on http://localhost:{}/mcp", port);
        System.out.println("Weather server started on port " + port);
        System.out.println("Connect your MCP client to http://localhost:" + port + "/mcp");

    }

    static McpServerHandle createServer(int port) {
        // language=json
        var inputSchema = MAPPER.readTree("""
            {
              "type": "object",
              "properties": {
                "city": {
                  "type": "string",
                  "description": "City name (e.g., London, Tokyo, New York)"
                },
                "units": {
                  "type": "string",
                  "enum": ["celsius", "fahrenheit"],
                  "description": "Temperature unit (default: celsius)"
                }
              },
              "required": ["city"]
            }
            """);

        var forecastPromptArgs = List.of(
            PromptArgument.of("forecast", null, "Original forecast text", true),
            PromptArgument.of("style", null, "Desired writing style (e.g., poetic, technical)", true));

        var handle = TachyonServer.builder()
            .name("weather-server")
            .description("Weather MCP server")
            .version("1.0.0")
            .toolsEnabled(true)
            .resourcesEnabled(true, true)
            .promptsEnabled(true)
            .tool(new GetWeatherTool(inputSchema))
            .resource(
                ResourceDescriptor.of(
                    "prediction-article",
                    "weather://prediction/article",
                    "Weather prediction article",
                    "text/markdown"),
                WeatherServer::handleArticleResource)
            .resource(
                ResourceDescriptor.of(
                    "current-weather-image",
                    "weather://current/image",
                    "Current weather icon",
                    "image/png"),
                WeatherServer::handleImageResource)
            .prompt(
                PromptDescriptor.of(
                    "rewrite-forecast", "Rewrite forecast in a given style", null, forecastPromptArgs, null),
                WeatherServer::handleRewriteForecast)
            .port(port)
            .start();

        var server = handle.server();
        server.resources().addTemplate(ResourceTemplateEntry.of(
            "forecast",
            "weather://forecast/{city}",
            "Weather forecast for a city",
            "application/json",
            city -> {
                var forecast = generateForecast(city);
                return TextResourceContents.of("weather://forecast/" + city, "application/json", forecast);
            }));

        return handle;
    }

    private WeatherServer() {
    }

    private static TextResourceContents handleArticleResource(McpContext ctx, ReadResourceRequest req) {
        // language=markdown
        var article = """
            # Weather Prediction

            Weather prediction uses physics-based models and statistical methods to forecast
            atmospheric conditions. Modern forecasting combines:

            - **Numerical Weather Prediction (NWP)** — solving fluid dynamics equations
              on a 3-D grid of the atmosphere
            - **Ensemble forecasting** — running multiple model perturbations to estimate
              confidence intervals
            - **Machine learning** — neural networks that learn from historical patterns
              and improve short-term nowcasting

            The global observing system includes weather stations, radiosondes, aircraft
            reports, ocean buoys, and over 30 polar-orbiting and geostationary satellites.

            *Published by Tachyon Weather MCP — %s*
            """.formatted(java.time.LocalDate.now());
        return TextResourceContents.of(req.uri(), "text/markdown", article);
    }

    private static BlobResourceContents handleImageResource(McpContext ctx, ReadResourceRequest req) {
        return BlobResourceContents.of(
            req.uri(),
            "image/png",
            PNG_BASE64,
            Map.of(
                "width", new IntNode(128),
                "height", new IntNode(128)
            )
        );
    }

    private static List<PromptMessage> handleRewriteForecast(@Nullable String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return List.of(PromptMessage.user("Please provide a forecast and a style."));
        }
        var args = MAPPER.readTree(arguments);
        var forecast = args.has("forecast") ? args.get("forecast").asString() : "unknown weather";
        var style = args.has("style") ? args.get("style").asString() : "plain";
        var message = "Rewrite the following weather forecast in a %s style:\n\n%s".formatted(style, forecast);
        return List.of(PromptMessage.user(message));
    }

    static String generateForecast(String city) {
        var temp = 5 + RNG.nextInt(30);
        var humidity = 30 + RNG.nextInt(60);
        var conditions = List.of("Sunny", "Cloudy", "Rainy", "Windy", "Foggy", "Partly cloudy");
        var condition = conditions.get(RNG.nextInt(conditions.size()));
        // language=json
        return """
            {
              "city": "%s",
              "forecast": {
                "condition": "%s",
                "temperature": %d,
                "humidity": %d
              },
              "updated": "%s"
            }
            """.formatted(city, condition, temp, humidity, Instant.now().toString());
    }

    private record GetWeatherTool(
        JsonNode inputSchema) implements SyncToolHandler<Map<String, JsonNode>, ToolResult> {

        @Override
        public String name() {
            return "get-weather";
        }

        @Override
        public String description() {
            return "Get current weather for a city";
        }

        @Override
        public ToolResult handle(McpContext context, Map<String, JsonNode> args) {
            var city = args.containsKey("city") ? args.get("city").asString() : "Unknown";
            var units = args.containsKey("units") ? args.get("units").asString() : "celsius";
            var weather = generateWeather(city, units);
            return ToolResult.text(weather);
        }

        private static String generateWeather(String city, String units) {
            var tempCelsius = -5 + RNG.nextInt(35);
            var tempDisplay =
                "celsius".equals(units) ? tempCelsius + "°C" : (tempCelsius * 9 / 5 + 32) + "°F";
            var conditions =
                List.of("Sunny", "Cloudy", "Rainy", "Windy", "Foggy", "Partly cloudy", "Thunderstorms");
            var condition = conditions.get(RNG.nextInt(conditions.size()));
            var humidity = 30 + RNG.nextInt(60);
            var windSpeed = 5 + RNG.nextInt(40);
            return """
                Weather in %s:
                  Condition: %s
                  Temperature: %s
                  Humidity: %d%%
                  Wind: %d km/h
                """.formatted(city, condition, tempDisplay, humidity, windSpeed);
        }
    }
}
