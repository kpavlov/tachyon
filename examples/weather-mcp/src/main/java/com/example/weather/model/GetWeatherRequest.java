/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package com.example.weather.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.jspecify.annotations.Nullable;

@JsonClassDescription("Input for looking up the current weather in a city.")
@JsonTypeName("GetWeatherRequest")
public record GetWeatherRequest(
        @JsonPropertyDescription("City name (e.g., London, Tokyo, New York)") String city,
        @JsonPropertyDescription("Temperature unit (default: celsius)") @Nullable TemperatureUnit units) {
}
