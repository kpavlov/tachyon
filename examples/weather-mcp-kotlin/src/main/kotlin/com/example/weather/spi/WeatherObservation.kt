// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.spi

data class WeatherObservation(
    val city: String,
    val condition: String,
    val temperatureCelsius: Double,
    val humidity: Int,
    val windSpeed: Double,
)
