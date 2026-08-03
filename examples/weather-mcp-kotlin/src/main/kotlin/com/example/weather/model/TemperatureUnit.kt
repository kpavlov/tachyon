/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather.model

import kotlinx.serialization.SerialName
import me.kpavlov.kt.schema.Description

@Description("Unit used to represent temperature")
@SerialName("TemperatureUnit")
enum class TemperatureUnit {
    Celsius,
    Fahrenheit,
}
