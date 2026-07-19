/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather;

import com.example.weather.spi.CityNotFoundException;
import com.example.weather.spi.CityProvider;
import com.example.weather.spi.WeatherObservation;
import com.example.weather.spi.WeatherProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class TestCityProvider implements CityProvider {

    private static final List<String> KNOWN_CITIES = List.of("London", "Los Angeles", "Tokyo", "Tallinn");




    @Override
    public CompletableFuture<List<String>> searchCities(String query) {
        if (query.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        var matches = KNOWN_CITIES.stream()
            .filter(city -> city.toLowerCase().startsWith(query.toLowerCase()))
            .toList();
        return CompletableFuture.completedFuture(matches);
    }
}
