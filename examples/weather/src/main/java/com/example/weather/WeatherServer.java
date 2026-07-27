/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package com.example.weather;

import com.example.weather.integration.OpenMeteoProvider;
import com.example.weather.service.NarrationStyle;
import com.example.weather.service.WeatherService;
import com.example.weather.spi.CityNotFoundException;
import com.example.weather.spi.WeatherObservation;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.protocol.api.server.domain.Annotations;
import dev.tachyonmcp.protocol.api.server.domain.Icon;
import dev.tachyonmcp.protocol.api.server.domain.InvalidArgumentException;
import dev.tachyonmcp.protocol.api.server.domain.PromptArgument;
import dev.tachyonmcp.protocol.api.server.domain.PromptMessage;
import dev.tachyonmcp.protocol.api.server.domain.ResourceContents;
import dev.tachyonmcp.protocol.api.server.domain.Role;
import dev.tachyonmcp.protocol.api.server.domain.TextResourceContents;
import dev.tachyonmcp.protocol.api.server.domain.UriTemplateValue;
import dev.tachyonmcp.protocol.api.server.features.completions.CompletionRequest;
import dev.tachyonmcp.protocol.api.server.features.completions.CompletionResult;
import dev.tachyonmcp.protocol.api.server.features.prompts.PromptRequest;
import dev.tachyonmcp.protocol.api.server.features.prompts.PromptResult;
import dev.tachyonmcp.protocol.api.server.features.resources.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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
        final var server = buildServer(8080);
        server.start();
        final var port = server.port();
        log.info("Connect your MCP client to http://localhost:{}/mcp", port);
    }

    static TachyonServer buildServer(int port) {
        return buildServer(port, weatherService);
    }

    static TachyonServer buildServer(int port, WeatherService weatherService) {
        var predictionArticle = weatherService.predictionArticle();
        var resourceAnnotations =
            Annotations.of(List.of(Role.USER, Role.ASSISTANT), 0.8, "2026-07-23T00:00:00Z");
        var resourceIcon = Icon.of(LOGO, "image/png", List.of("256x256"), "light");
        return TachyonServer.builder()
                .info(it -> it
                        .name("weather-server")
                        .title("Weather Server")
                        .description("Weather MCP server")
                        .websiteUrl("https://github.com/kpavlov/tachyon/tree/main/examples/weather")
                        .instructions("Test instructions")
                        .icons(Icon.of(LOGO, "image/png", List.of("256x256"), null))
                        .version("1.0"))
                .withTools(tools -> tools.register(GetWeatherTool.create(weatherService)))
                .withResources(resources -> resources.register(
                        resource -> resource.name("prediction-article")
                                .uri("weather://prediction/article")
                                .description("Weather prediction article")
                                .title("Weather Prediction")
                                .annotations(resourceAnnotations)
                                .size(predictionArticle.getBytes(StandardCharsets.UTF_8).length)
                                .icons(List.of(resourceIcon))
                                .mimeType("text/markdown"),
                        ResourceHandler.of((ctx, uri) ->
                                TextResourceContents.of(uri, predictionArticle, "text/markdown")))
                .registerAsync(
                        resource -> resource.name("featured-current-weather")
                                .uri("weather://featured/current")
                                .description("Current weather in Tallinn")
                                .title("Featured Current Weather")
                                .annotations(resourceAnnotations)
                                .icons(List.of(resourceIcon))
                                .mimeType("application/json"),
                        ResourceHandler.ofAsync((ctx, uri) -> weatherService.currentWeatherAsync("Tallinn")
                                .thenApply(weather ->
                                        TextResourceContents.of(uri, asJson(weather), "application/json")))))
                .withPrompts(prompts -> prompts.register(
                        prompt -> prompt.name("rewrite-forecast")
                                .description("Rewrites a weather forecast in a chosen style")
                                .addArguments(
                                        PromptArgument.of("forecast", "Forecast", "Weather forecast to rewrite", true),
                                        PromptArgument.of("style", "Style", "plain, concise, or pirate", true))
                                .inputSchema(NarrationStyle.inputSchema()),
                        (ctx, request) -> rewriteForecast(weatherService, request)))
                .withResources(resources -> resources.registerTemplate(
                        template -> template.name("current-weather")
                                .uriTemplate("weather://current/{city}")
                                .title("Weather in the city")
                                .description("Weather forecast for a city")
                                .mimeType("application/json"),
                        (ctx, request) ->
                                handleWeatherTemplate(weatherService, request.uri(), request.params())))
                .withCompletions(completions -> completions
                        .registerForPrompt("rewrite-forecast", (ctx, request) -> completeStyle(request))
                        .registerForResourceAsync(
                                "weather://current/{city}",
                                (ctx, request) -> {
                                    if (!"city".equals(request.argumentName())) {
                                        return CompletableFuture.completedFuture(CompletionResult.of(List.of()));
                                    }
                                    return weatherService.searchCities(request.argumentValue())
                                            .thenApply(CompletionResult::of)
                                            .exceptionally(e -> CompletionResult.of(List.of()));
                                }))
                .session(session -> session.enabled(true))
                .network(network -> network.port(port))
                .build();
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
