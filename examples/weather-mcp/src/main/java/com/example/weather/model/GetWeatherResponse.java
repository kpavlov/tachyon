/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonClassDescription("Current weather observation for a city.")
@JsonTypeName("GetWeatherResponse")
public record GetWeatherResponse(
        @JsonPropertyDescription("City name") String city,
        @JsonPropertyDescription("Weather condition") String condition,
        @JsonPropertyDescription("Temperature in the response unit") double temperature,
        @JsonPropertyDescription("Temperature unit") TemperatureUnit unit,
        @JsonPropertyDescription("Relative humidity percentage") int humidity,
        @JsonPropertyDescription("Wind speed in km/h") double windSpeed) {
}
