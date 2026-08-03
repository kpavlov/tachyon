/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather.spi

import com.example.weather.model.TemperatureUnit
import kotlinx.serialization.Serializable

@Serializable
data class WeatherObservation(
    val condition: String,
    val temperature: Double,
    val temperatureUnit: TemperatureUnit,
    val humidity: Int,
    val windSpeed: Double,
)
