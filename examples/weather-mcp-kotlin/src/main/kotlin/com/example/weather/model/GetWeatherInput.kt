package com.example.weather.model

import me.kpavlov.kt.schema.Description

data class GetWeatherInput(
    @Description("City name (e.g., London, Tokyo, New York)")
    val city: String,
    @Description("Temperature unit (default: Celsius)")
    val units: TemperatureUnit = TemperatureUnit.Celsius,
)
