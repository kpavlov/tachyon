/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather.model

import kotlinx.serialization.SerialName
import me.kpavlov.kt.schema.Description

@SerialName("GetWeatherRequest")
data class GetWeatherRequest(
    @Description("City name (e.g., London, Tokyo, New York)")
    val city: String,
    @Description("Temperature unit (default: Celsius)")
    val units: TemperatureUnit = TemperatureUnit.Celsius,
)
