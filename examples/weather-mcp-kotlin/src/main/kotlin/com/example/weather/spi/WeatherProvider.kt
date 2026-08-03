/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather.spi

import com.example.weather.model.TemperatureUnit

fun interface WeatherProvider {
    fun currentWeather(
        city: String,
        temperatureUnit: TemperatureUnit,
    ): WeatherObservation
}
