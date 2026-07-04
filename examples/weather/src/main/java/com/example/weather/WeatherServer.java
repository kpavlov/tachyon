/* Copyright (c) 2026 Konstantin Pavlov. */

package com.example.weather;

import dev.tachyonmcp.server.ServerHandle;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import dev.tachyonmcp.runtime.InteractionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class WeatherServer {

    private static final Logger log = LoggerFactory.getLogger(WeatherServer.class);

    public static void main(String... args) {
        final var handle = createServer(8080);
        final var port = handle.port();
        log.info("Connect your MCP client to http://localhost:{}/mcp", port);
    }

    static ServerHandle createServer(int port) {
        var handle = TachyonServer.builder()
                .info(it -> it
                        .name("weather-server")
                        .title("Weather Server")
                        .description("Weather MCP server")
                        .websiteUrl("http://localhost:8080/mcp")
                        .instructions("Test instructions")
                        .version("1.0"))
                .session(s->s.stateless(true))
                .tool(new GetWeatherTool())
                .resource(
                        ResourceDescriptor.of(
                                "prediction-article",
                                "weather://prediction/article",
                                "Weather prediction article",
                                "text/markdown"),
                        WeatherServer::handleArticleResource)
                .resource(
                        ResourceDescriptor.of(
                                "weather-image",
                                "weather://current/image",
                                "Current weather icon",
                                "image/png"),
                        (ctx, req) -> WeatherImageResource.read())
                .prompt(
                        PromptDescriptor.of("rewrite-forecast", "Rewrites a weather forecast in a given style"),
                    WeatherServer::handleRewriteForecast)
                .port(port)
                .start();

        handle.server().resources()
                .addTemplate(ResourceTemplateEntry.of(
                        "forecast",
                        "weather://forecast/{city}",
                        "Weather forecast for a city",
                        "application/json",
                        WeatherServer::handleForecastTemplate));

        return handle;
    }

    private WeatherServer() {
    }

    private static TextResourceContents handleArticleResource(InteractionContext ctx, ReadResourceRequest req) {
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

                ---
                Published by Tachyon Weather MCP — %s
            """.formatted(LocalDate.now());
        return TextResourceContents.of(req.uri(), "text/markdown", article);
    }

    private static ResourceContents handleForecastTemplate(InteractionContext ctx, String uri, Map<String, String> params) {
        var city = params.get("city");
        var forecast = """
                {
                  "city": "%s",
                  "condition": "Partly cloudy",
                  "temperature": 22,
                  "unit": "celsius",
                  "humidity": 55,
                  "wind": 15
                }
                """.formatted(city);
        return TextResourceContents.of(uri, "application/json", forecast);
    }

    private static List<PromptMessage> handleRewriteForecast(String arguments) {
        return List.of(PromptMessage.user("Rewrite this forecast in a pirate style."));
    }

}
