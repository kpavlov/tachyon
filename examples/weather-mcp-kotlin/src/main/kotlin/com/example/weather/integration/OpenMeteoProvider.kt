// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather.integration

import com.example.weather.spi.CityNotFoundException
import com.example.weather.spi.CityProvider
import com.example.weather.spi.WeatherCondition
import com.example.weather.spi.WeatherObservation
import com.example.weather.spi.WeatherProvider
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class OpenMeteoProvider(
    private val httpClient: HttpClient,
) : WeatherProvider, CityProvider {
    override fun currentWeather(city: String): WeatherObservation {
        val location = location(city, get(geocodingRequest(city, count = 1)))
        val forecast = get(forecastRequest(location))
        return weather(city, forecast)
    }

    override fun searchCities(query: String): List<String> {
        if (query.length < 2) return emptyList()
        return runCatching { cityNames(get(geocodingRequest(query, count = 10))) }.getOrDefault(emptyList())
    }

    private fun get(request: HttpRequest): String {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Open-Meteo request failed with HTTP ${response.statusCode()}" }
        return response.body()
    }

    private fun location(
        city: String,
        response: String,
    ): Location {
        val result = parse(response).path("results").path(0)
        if (result.isMissingNode) throw CityNotFoundException(city)
        return Location(result.path("latitude").asDouble(), result.path("longitude").asDouble())
    }

    private fun weather(
        city: String,
        response: String,
    ): WeatherObservation {
        val current = parse(response).path("current")
        return WeatherObservation(
            city = city,
            condition = condition(current.path("weather_code").asInt()).displayName,
            temperatureCelsius = current.path("temperature_2m").asDouble(),
            humidity = current.path("relative_humidity_2m").asInt(),
            windSpeed = current.path("wind_speed_10m").asDouble(),
        )
    }

    private fun cityNames(response: String): List<String> =
        parse(response)
            .path("results")
            .mapNotNull { it.path("name").asString()?.takeIf(String::isNotBlank) }
            .distinct()

    private data class Location(val latitude: Double, val longitude: Double)

    companion object {
        private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        private val MAPPER = ObjectMapper()

        private fun geocodingRequest(
            query: String,
            count: Int,
        ): HttpRequest {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
            return HttpRequest
                .newBuilder(URI.create("$GEOCODING_URL?name=$encoded&count=$count&language=en"))
                .GET()
                .build()
        }

        private fun forecastRequest(location: Location): HttpRequest {
            val uri =
                "$FORECAST_URL?latitude=${location.latitude}&longitude=${location.longitude}" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&wind_speed_unit=kmh"
            return HttpRequest.newBuilder(URI.create(uri)).GET().build()
        }

        private fun parse(response: String): JsonNode =
            try {
                MAPPER.readTree(response)
            } catch (e: Exception) {
                throw IllegalStateException("Invalid Open-Meteo response", e)
            }

        private fun condition(weatherCode: Int): WeatherCondition =
            when (weatherCode) {
                0 -> WeatherCondition.CLEAR_SKY
                1, 2 -> WeatherCondition.PARTLY_CLOUDY
                3 -> WeatherCondition.OVERCAST
                45, 48 -> WeatherCondition.FOGGY
                51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAINY
                71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOWY
                95, 96, 99 -> WeatherCondition.THUNDERSTORMS
                else -> WeatherCondition.UNKNOWN
            }
    }
}
