/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */
package com.example.weather;

import com.example.weather.service.WeatherService;
import com.example.weather.spi.CityNotFoundException;
import com.example.weather.spi.WeatherObservation;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class GetWeatherTool {
    private static final Logger log = LoggerFactory.getLogger(GetWeatherTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // language=json
    private static final String INPUT_SCHEMA = """
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
        """;

    static ToolHandler create(WeatherService weatherService) {
        return ToolHandler.of(b -> b
                .name("get-weather")
                .title("Current Weather")
                .description("Get current weather for a city")
                .inputSchema(INPUT_SCHEMA),
            (ctx, args) -> {
                var city = args.stringValue("city");
                var units = args.stringOr("units", "celsius");
                try {
                    return ToolResult.text(format(weatherService.currentWeather(city), units));
                } catch (CityNotFoundException e) {
                    var elicitedCity = elicitCity(ctx, city);
                    if (elicitedCity.isEmpty()) {
                        return ToolResult.error("City not found");
                    }
                    try {
                        return ToolResult.text(format(weatherService.currentWeather(elicitedCity.get()), units));
                    } catch (CityNotFoundException ignored) {
                        return ToolResult.error("City not found");
                    } catch (Exception x) {
                        return internalError(x);
                    }
                } catch (Exception e) {
                    return internalError(e);
                }
            });
    }

    private static ToolResult internalError(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.warn("get-weather failed", e);
        return ToolResult.error("Could not get weather");
    }

    private static Optional<String> elicitCity(dev.tachyonmcp.runtime.InteractionContext ctx, String city) throws Exception {
        var future = ctx.sendRequest(
                "elicitation/create",
                Map.of(
                        "mode", "form",
                        "message", "City '%s' was not found. Enter another city.".formatted(city),
                        "requestedSchema",
                        Map.of(
                                "type", "object",
                                "properties", Map.of("city", Map.of("type", "string", "title", "City")),
                                "required", List.of("city"))));
        String response;
        try {
            response = future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        var result = MAPPER.readTree(response);
        if (!"accept".equals(result.path("action").asString())) {
            return Optional.empty();
        }
        var correctedCity = result.path("content").path("city").asString();
        return correctedCity.isBlank() ? Optional.empty() : Optional.of(correctedCity);
    }

    private static String format(WeatherObservation weather, String units) {
        var temperature = "celsius".equals(units)
                ? "%.1f°C".formatted(weather.temperatureCelsius())
                : "%.1f°F".formatted(weather.temperatureCelsius() * 9 / 5 + 32);
        return """
            Weather in %s:
              Condition: %s
              Temperature: %s
              Humidity: %d%%
              Wind: %.1f km/h
            """.formatted(
                weather.city(), weather.condition(), temperature, weather.humidity(), weather.windSpeed());
    }
}
