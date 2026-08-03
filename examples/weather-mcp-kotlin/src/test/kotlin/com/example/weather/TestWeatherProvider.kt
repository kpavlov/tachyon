/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather

import com.example.weather.model.TemperatureUnit
import com.example.weather.spi.CityNotFoundException
import com.example.weather.spi.WeatherObservation
import com.example.weather.spi.WeatherProvider

class TestWeatherProvider : WeatherProvider {
    override fun currentWeather(
        city: String,
        temperatureUnit: TemperatureUnit,
    ): WeatherObservation {
        if (city == "Unknown") throw CityNotFoundException(city)
        return WeatherObservation(
            condition = "Clear sky",
            temperature = 18.5,
            temperatureUnit = temperatureUnit,
            humidity = 52,
            windSpeed = 12.0,
        )
    }
}
