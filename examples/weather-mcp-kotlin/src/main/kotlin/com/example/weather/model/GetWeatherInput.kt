package com.example.weather.model

import me.kpavlov.kt.schema.Description

enum class TemperatureUnit {
    celsius, fahrenheit
}

data class GetWeatherInput(
    @Description("City name (e.g., London, Tokyo, New York)")
    val city: String,
    @Description("Temperature unit (default: celsius)")
    val units: TemperatureUnit? = null,
)
