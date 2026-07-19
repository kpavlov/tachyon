/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package com.example.weather.spi;

import java.util.concurrent.CompletableFuture;

public interface WeatherProvider {

    WeatherObservation currentWeather(String city) throws Exception;

    CompletableFuture<WeatherObservation> currentWeatherAsync(String city);

}
