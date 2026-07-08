/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */
package com.example.weather;

import dev.tachyonmcp.server.features.tools.*;
import dev.tachyonmcp.runtime.InteractionContext;

import java.util.List;
import java.util.random.RandomGenerator;

class GetWeatherTool extends AbstractSyncToolHandler {
    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();
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

    public GetWeatherTool() {
        super(ToolDescriptor.builder()
            .name("get-weather")
            .title("Current Weather")
            .description("Get current weather for a city")
            .inputSchema(INPUT_SCHEMA)
            .build());
    }


    @Override
    public ToolResult handle(InteractionContext context, ToolArgs args) {
        var city = args.string("city");
        var units = args.stringOr("units", "celsius");
        return ToolResult.text(generateWeather(city, units));
    }

    private static String generateWeather(String city, String units) {
        var tempCelsius = -5 + RANDOM.nextInt(35);
        var tempDisplay = "celsius".equals(units) ? tempCelsius + "°C" : (tempCelsius * 9 / 5 + 32) + "°F";
        var conditions = List.of("Sunny", "Cloudy", "Rainy", "Windy", "Foggy", "Partly cloudy", "Thunderstorms");
        var condition = conditions.get(RANDOM.nextInt(conditions.size()));
        var humidity = 30 + RANDOM.nextInt(60);
        var windSpeed = 5 + RANDOM.nextInt(40);
        return """
            Weather in %s:
              Condition: %s
              Temperature: %s
              Humidity: %d%%
              Wind: %d km/h
            """.formatted(city, condition, tempDisplay, humidity, windSpeed);
    }
}
