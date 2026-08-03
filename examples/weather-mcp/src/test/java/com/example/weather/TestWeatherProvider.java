/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather;

import com.example.weather.spi.CityNotFoundException;
import com.example.weather.spi.WeatherObservation;
import com.example.weather.spi.WeatherProvider;

import java.util.concurrent.CompletableFuture;

final class TestWeatherProvider implements WeatherProvider {

    @Override
    public WeatherObservation currentWeather(String city) throws CityNotFoundException {
        if ("Unknown".equals(city)) {
            throw new CityNotFoundException(city);
        }
        return new WeatherObservation(city, "Clear sky", 18.5, 52, 12.0);
    }

    @Override
    public CompletableFuture<WeatherObservation> currentWeatherAsync(String city) {
        try {
            return CompletableFuture.completedFuture(currentWeather(city));
        } catch (CityNotFoundException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
