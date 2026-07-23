// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather

import com.example.weather.spi.CityNotFoundException
import com.example.weather.spi.WeatherObservation
import com.example.weather.spi.WeatherProvider

class TestWeatherProvider : WeatherProvider {
    override fun currentWeather(city: String): WeatherObservation {
        if (city == "Unknown") throw CityNotFoundException(city)
        return WeatherObservation(city, "Clear sky", 18.5, 52, 12.0)
    }
}
