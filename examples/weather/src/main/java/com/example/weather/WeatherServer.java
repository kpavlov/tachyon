/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package com.example.weather;

import com.example.weather.integration.OpenMeteoProvider;
import com.example.weather.service.NarrationStyle;
import com.example.weather.service.WeatherService;
import com.example.weather.spi.CityNotFoundException;
import com.example.weather.spi.WeatherObservation;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.Icon;
import dev.tachyonmcp.server.domain.InvalidArgumentException;
import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.ResourceContents;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import dev.tachyonmcp.server.features.completions.CompletionRequest;
import dev.tachyonmcp.server.features.completions.CompletionResult;
import dev.tachyonmcp.server.features.prompts.PromptRequest;
import dev.tachyonmcp.server.features.prompts.PromptResult;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public final class WeatherServer {

    private static final Logger log = LoggerFactory.getLogger(WeatherServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LOGO = classpathDataUri("/images/logo.png", "image/png");

    private static final WeatherService weatherService;

    static {
        HttpClient httpClient = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
        final var openMeteoProvider = new OpenMeteoProvider(httpClient);
        weatherService = new WeatherService(openMeteoProvider, openMeteoProvider);
    }

    public static void main(String... args) {
        final var server = createServer(8080);
        final var port = server.port();
        log.info("Connect your MCP client to http://localhost:{}/mcp", port);
    }

    static TachyonServer createServer(int port) {
        return createServer(port, weatherService);
    }

    static TachyonServer createServer(int port, WeatherService weatherService) {
        return TachyonServer.builder()
                .info(it -> it
                        .name("weather-server")
                        .title("Weather Server")
                        .description("Weather MCP server")
                        .websiteUrl("https://github.com/kpavlov/tachyon/tree/main/examples/weather")
                        .instructions("Test instructions")
                        .icons(Icon.of(LOGO, "image/png", List.of("256x256"), null))
                        .version("1.0"))
                .tool(GetWeatherTool.create(weatherService))
                .resource(
                        resource -> resource.name("prediction-article")
                                .uri("weather://prediction/article")
                                .description("Weather prediction article")
                                .mimeType("text/markdown"),
                    ResourceHandler.of((ctx, uri) ->
                        TextResourceContents.of(uri, weatherService.predictionArticle(), "text/markdown")))
                .asyncResource(
                        resource -> resource.name("featured-current-weather")
                                .uri("weather://featured/current")
                                .description("Current weather in Tallinn")
                                .mimeType("application/json"),
                    ResourceHandler.ofAsync((ctx, uri) -> weatherService.currentWeatherAsync("Tallinn")
                        .thenApply(weather -> TextResourceContents.of(uri, asJson(weather), "application/json"))))
                .prompt(
                        prompt -> prompt.name("rewrite-forecast")
                                .description("Rewrites a weather forecast in a chosen style")
                                .addArguments(
                                        PromptArgument.of("forecast", "Forecast", "Weather forecast to rewrite", true),
                                        PromptArgument.of("style", "Style", "plain, concise, or pirate", true))
                                .inputSchema(NarrationStyle.inputSchema()),
                        (ctx, request) -> rewriteForecast(weatherService, request))
            .promptCompletion("rewrite-forecast", (ctx, request) -> completeStyle(request))
                .resourceTemplate(
                        template -> template.name("current-weather")
                                .uriTemplate("weather://current/{city}")
                                .title("Weather in the city")
                                .description("Weather forecast for a city")
                                .mimeType("application/json"),
                        (ctx, uri, params, uriTemplate) ->
                                handleWeatherTemplate(weatherService, uri, params))
            .asyncResourceCompletion(
                "weather://current/{city}",
                (ctx, request) -> {
                    if (!"city".equals(request.argumentName())) {
                        return CompletableFuture.completedFuture(CompletionResult.of(List.of()));
                    }
                    return weatherService.searchCities(request.argumentValue())
                        .thenApply(CompletionResult::of)
                        .exceptionally(e -> CompletionResult.of(List.of()));
                })
                .session(session -> session.enabled(true))
                .network(network -> network.port(port))
                .start();
    }

    private WeatherServer() {
    }

    private static CompletionResult completeStyle(CompletionRequest request) {
        if (!"style".equals(request.argumentName())) {
            return CompletionResult.of(List.of());
        }
        var query = request.argumentValue().toLowerCase(Locale.ROOT);
        var matches = NarrationStyle.styleNames().stream()
            .filter(style -> style.startsWith(query))
            .toList();
        return CompletionResult.of(matches);
    }

    private static PromptResult rewriteForecast(WeatherService weatherService, PromptRequest request) {
        var arguments = MAPPER.readTree(request.arguments() != null ? request.arguments() : "{}");
        var forecast = arguments.path("forecast").asString();
        var style = NarrationStyle.from(arguments.path("style").asString());
        return PromptResult.messages(List.of(PromptMessage.user(weatherService.rewriteForecastInstruction(forecast, style))));
    }

    private static ResourceContents handleWeatherTemplate(
            WeatherService weatherService,
            String uri,
            Map<String, UriTemplateValue> params) {
        var city = ((UriTemplateValue.Scalar) params.get("city")).value();
        try {
            return TextResourceContents.of(uri, asJson(weatherService.currentWeather(city)), "application/json");
        } catch (CityNotFoundException e) {
            throw new InvalidArgumentException("city", e.getMessage());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not get weather", e);
        }
    }

    private static String asJson(WeatherObservation weather) {
        try {
            return MAPPER.writeValueAsString(weather);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize weather", e);
        }
    }

    private static String classpathDataUri(String path, String mimeType) {
        try (var image = WeatherServer.class.getResourceAsStream(path)) {
            if (image == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return "data:%s;base64,%s".formatted(mimeType, Base64.getEncoder().encodeToString(image.readAllBytes()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read classpath resource: " + path, e);
        }
    }

}
