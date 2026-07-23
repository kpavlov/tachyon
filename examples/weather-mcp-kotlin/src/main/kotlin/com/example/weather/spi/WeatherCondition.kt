// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.spi

enum class WeatherCondition(
    val displayName: String,
) {
    CLEAR_SKY("Clear sky"),
    PARTLY_CLOUDY("Partly cloudy"),
    OVERCAST("Overcast"),
    FOGGY("Foggy"),
    RAINY("Rainy"),
    SNOWY("Snowy"),
    THUNDERSTORMS("Thunderstorms"),
    UNKNOWN("Unknown"),
}
