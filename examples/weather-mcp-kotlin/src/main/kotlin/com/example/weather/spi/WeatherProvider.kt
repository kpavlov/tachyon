// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.spi

fun interface WeatherProvider {
    fun currentWeather(city: String): WeatherObservation
}
