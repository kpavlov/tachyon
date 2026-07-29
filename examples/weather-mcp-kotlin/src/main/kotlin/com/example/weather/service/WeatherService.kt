// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.service

import com.example.weather.model.TemperatureUnit
import com.example.weather.spi.CityProvider
import com.example.weather.spi.WeatherObservation
import com.example.weather.spi.WeatherProvider

class WeatherService(
    private val weatherProvider: WeatherProvider,
    private val cityProvider: CityProvider,
) {
    fun currentWeather(city: String, temperatureUnit: TemperatureUnit): WeatherObservation =
        weatherProvider.currentWeather(
            city = city,
            temperatureUnit = temperatureUnit
        )

    fun searchCities(query: String): List<String> = cityProvider.searchCities(query)

    val predictionArticle: String by lazy { loadPredictionArticle() }

    fun rewriteForecastInstruction(
        forecast: String,
        style: NarrationStyle,
    ): String {
        require(forecast.isNotBlank()) { "forecast is required" }
        return "Rewrite the following weather forecast in ${style.value} style. " +
            "Preserve factual details:\n\n```${forecast}\n```"
    }

    private fun loadPredictionArticle(): String {
        val article =
            WeatherService::class.java.getResourceAsStream("/articles/prediction-article.md")
                ?: error("Missing prediction article")
        return article.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
