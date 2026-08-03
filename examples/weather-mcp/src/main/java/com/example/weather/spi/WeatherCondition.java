/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.spi;

public enum WeatherCondition {
    CLEAR_SKY("Clear sky"),
    PARTLY_CLOUDY("Partly cloudy"),
    OVERCAST("Overcast"),
    FOGGY("Foggy"),
    RAINY("Rainy"),
    SNOWY("Snowy"),
    THUNDERSTORMS("Thunderstorms"),
    UNKNOWN("Unknown");

    private final String displayName;

    WeatherCondition(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
