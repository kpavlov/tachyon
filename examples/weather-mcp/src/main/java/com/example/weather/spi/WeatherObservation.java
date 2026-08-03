/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather.spi;

public record WeatherObservation(
    String condition, double temperatureCelsius, int humidity, double windSpeed) {
}
