/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.spi;

public record WeatherObservation(
    String city, String condition, double temperatureCelsius, int humidity, double windSpeed) {
}
