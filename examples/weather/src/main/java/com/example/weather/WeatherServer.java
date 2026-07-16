/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package com.example.weather;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.ReadResourceRequest;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptRequest;
import dev.tachyonmcp.server.features.prompts.PromptResult;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class WeatherServer {

    private static final Logger log = LoggerFactory.getLogger(WeatherServer.class);

    public static void main(String... args) {
        final var server = createServer(8080);
        final var port = server.port();
        log.info("Connect your MCP client to http://localhost:{}/mcp", port);
    }

    /**
     * Creates and starts the weather MCP server on the specified port.
     *
     * @param port the port on which the server listens
     * @return the started weather MCP server
     */
    static TachyonServer createServer(int port) {
        return TachyonServer.builder()
                .info(it -> it
                        .name("weather-server")
                        .title("Weather Server")
                        .description("Weather MCP server")
                        .websiteUrl("http://localhost:8080/mcp")
                        .instructions("Test instructions")
                        .version("1.0"))
                .session(s -> s.enabled(false))
                .tool(GetWeatherTool.create())
                .resource(
                        ResourceDescriptor.of(
                                "prediction-article",
                                "weather://prediction/article",
                                "Weather prediction article",
                                "text/markdown"),
                        WeatherServer::handleArticleResource)
                .asyncResource(
                        resource -> resource.name("weather-image")
                                .uri("weather://current/image")
                                .description("Current weather icon")
                                .mimeType("image/png"),
                        (ctx, req) -> CompletableFuture.supplyAsync(WeatherImageResource::read))

            .prompt(PromptDescriptor.of("rewrite-forecast", "Rewrites a weather forecast in a given style"),
                    WeatherServer::handleRewriteForecast)

            .resourceTemplate(builder -> builder
                    .name("forecast")
                    .uriTemplate("weather://forecast/{city}")
                    .title("Weather in the city")
                    .description("Weather forecast for a city")
                    .mimeType("application/json"),
                WeatherServer::handleForecastTemplate)
                .port(port)
                .start();
    }

    private WeatherServer() {
    }

    /**
     * Provides a Markdown article explaining weather prediction methods and observational systems.
     *
     * @param req the request containing the resource URI
     * @return Markdown resource contents for the requested URI
     */
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

    /**
     * Creates a JSON weather forecast resource for the requested city.
     *
     * @param uri    the resource URI
     * @param params the URI template parameters containing the city
     * @return       JSON resource contents for the city forecast
     */
    private static ResourceContents handleForecastTemplate(
            InteractionContext ctx, String uri, Map<String, UriTemplateValue> params) {
        var city = ((UriTemplateValue.Scalar) params.get("city")).value();
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

    private static PromptResult handleRewriteForecast(InteractionContext ctx, PromptRequest request) {
        return PromptResult.messages(List.of(PromptMessage.user("Rewrite this forecast in a pirate style.")));
    }

}
