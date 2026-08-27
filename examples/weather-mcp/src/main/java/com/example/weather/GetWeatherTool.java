/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */
package com.example.weather;

import com.example.weather.model.CityInput;
import com.example.weather.model.GetWeatherRequest;
import com.example.weather.model.GetWeatherResponse;
import com.example.weather.model.TemperatureUnit;
import com.example.weather.service.WeatherService;
import com.example.weather.spi.CityNotFoundException;
import com.example.weather.spi.WeatherObservation;
import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.FormInputRequest;
import dev.tachyonmcp.api.server.domain.Icon;
import dev.tachyonmcp.api.server.domain.ProgressToken;
import dev.tachyonmcp.api.server.domain.ToolAnnotations;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolFn;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class GetWeatherTool {
    private static final Logger log = LoggerFactory.getLogger(GetWeatherTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchema CITY_SCHEMA = JsonSchema.generate(CityInput.class);
    private static final JsonSchema INPUT_SCHEMA = JsonSchema.generate(GetWeatherRequest.class);
    private static final JsonSchema OUTPUT_SCHEMA = JsonSchema.generate(GetWeatherResponse.class);
    private static final String CITY_INPUT_KEY = "city";

    static final ToolDescriptor DESCRIPTOR = ToolDescriptor.builder()
        .name("get-weather")
        .title("Current Weather")
        .description("Get current weather for a city")
        .inputSchema(INPUT_SCHEMA)
        .outputSchema(OUTPUT_SCHEMA)
        .icons(Icon.of(WeatherServer.SUN_AND_CLOUD, "image/png", List.of("128x128"), null))
        .annotations(ToolAnnotations.of(null, true, false, true, true))
        .build();

    static ToolFn fn(WeatherService weatherService) {
        return (ctx, request) -> {
            var args = request.arguments().decode(GetWeatherRequest.class);
            var units = args.units();
            var progressToken = request.progressToken();
            var inputResponses = request.inputResponses();
            var city = elicitedCity(inputResponses).orElseGet(args::city);
            try {
                return ToolResult.structured(
                    toResponse(city, fetchWithProgress(ctx, progressToken, weatherService, city), units)
                );
            } catch (CityNotFoundException e) {
                if (inputResponses != null) {
                    return ToolResult.error("City not found");
                }
                var inputRequests = Map.of(
                    CITY_INPUT_KEY,
                    FormInputRequest.of(
                        "City '%s' was not found. Enter another city.".formatted(city), citySchemaMap()));
                return ToolResult.inputRequired(inputRequests, null);
            } catch (Exception e) {
                return internalError(e);
            }
        };
    }

    private static WeatherObservation fetchWithProgress(
        InteractionContext ctx, ProgressToken progressToken, WeatherService weatherService, String city)
        throws Exception {
        ctx.notifications().progress(progressToken, 0.1, 1.0, "Fetching weather for " + city);
        var weather = weatherService.currentWeather(city);
        ctx.notifications().progress(progressToken, 1.0, 1.0, "Weather retrieved for " + city);
        return weather;
    }

    private static ToolResult internalError(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.warn("get-weather failed", e);
        return ToolResult.error("Could not get weather");
    }

    private static Optional<String> elicitedCity(@Nullable Map<String, Object> inputResponses) {
        if (inputResponses == null) {
            return Optional.empty();
        }
        if (inputResponses.get(CITY_INPUT_KEY) instanceof Map<?, ?> response
            && response.get("city") instanceof String correctedCity
            && !correctedCity.isBlank()) {
            return Optional.of(correctedCity);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> citySchemaMap() {
        return (Map<String, Object>) MAPPER.readValue(CITY_SCHEMA.json(), Map.class);
    }

    private static GetWeatherResponse toResponse(String city, WeatherObservation weather, @Nullable TemperatureUnit units) {
        var fahrenheit = units == TemperatureUnit.FAHRENHEIT;
        var temperature = fahrenheit ? weather.temperatureCelsius() * 9 / 5 + 32 : weather.temperatureCelsius();
        var unit = fahrenheit ? TemperatureUnit.FAHRENHEIT : TemperatureUnit.CELSIUS;
        return new GetWeatherResponse(
            city, weather.condition(), temperature, unit, weather.humidity(), weather.windSpeed());
    }
}
