// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather

import com.example.weather.service.WeatherService
import com.example.weather.spi.CityNotFoundException
import com.example.weather.spi.WeatherObservation
import dev.tachyonmcp.runtime.InteractionContext
import dev.tachyonmcp.server.kotlin.config.ToolScope
import dev.tachyonmcp.server.domain.ProgressToken
import dev.tachyonmcp.server.features.tools.ToolDescriptor
import dev.tachyonmcp.server.kotlin.features.tools.ToolDescriptor
import dev.tachyonmcp.server.features.tools.ToolResult
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val log = LoggerFactory.getLogger("com.example.weather.GetWeatherTool")
private val MAPPER = ObjectMapper()
private const val ELICITATION_TIMEOUT_SECONDS = 600L

// language=json
private const val INPUT_SCHEMA = """
{
  "type": "object",
  "properties": {
    "city": {
      "type": "string",
      "description": "City name (e.g., London, Tokyo, New York)"
    },
    "units": {
      "type": "string",
      "enum": ["celsius", "fahrenheit"],
      "description": "Temperature unit (default: celsius)"
    }
  },
  "required": ["city"]
}
"""

fun getWeatherToolDescriptor(): ToolDescriptor =
    ToolDescriptor {
        name = "get-weather"
        title = "Current Weather"
        description = "Get current weather for a city"
        inputSchema(INPUT_SCHEMA)
    }

fun ToolScope.getWeather(weatherService: WeatherService): ToolResult {
    val args = request.arguments()
    val city = args.stringValue("city")
    val units = args.stringOr("units", "celsius") ?: "celsius"
    val progressToken = request.progressToken()

    fun render(city: String) =
        ToolResult.text(format(fetchWithProgress(ctx, progressToken, weatherService, city), units))

    return try {
        render(city)
    } catch (_: CityNotFoundException) {
        val elicitedCity = elicitCity(ctx, city) ?: return ToolResult.error("City not found")
        try {
            render(elicitedCity)
        } catch (_: CityNotFoundException) {
            ToolResult.error("City not found")
        } catch (x: Exception) {
            internalError(x)
        }
    } catch (e: Exception) {
        internalError(e)
    }
}

private fun fetchWithProgress(
    ctx: InteractionContext,
    progressToken: ProgressToken?,
    weatherService: WeatherService,
    city: String,
): WeatherObservation {
    ctx.notifications().progress(progressToken, 0.1, 1.0, "Fetching weather for $city")
    val weather = weatherService.currentWeather(city)
    ctx.notifications().progress(progressToken, 1.0, 1.0, "Weather retrieved for $city")
    return weather
}

internal fun restoreInterruptStatus(e: Exception) {
    if (e is InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

private fun internalError(e: Exception): ToolResult {
    restoreInterruptStatus(e)
    log.warn("get-weather failed", e)
    return ToolResult.error("Could not get weather")
}

private fun elicitCity(
    ctx: InteractionContext,
    city: String,
): String? {
    val future =
        ctx.sendRequest(
            "elicitation/create",
            mapOf(
                "mode" to "form",
                "message" to "City '$city' was not found. Enter another city.",
                "requestedSchema" to
                    mapOf(
                        "type" to "object",
                        "properties" to
                            mapOf(
                                "city" to
                                    mapOf(
                                        "type" to "string",
                                        "title" to "City",
                                    ),
                            ),
                        "required" to listOf("city"),
                    ),
            ),
        )
    val response =
        try {
            future.get(ELICITATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (_: TimeoutException) {
            return null
        }
    val result = MAPPER.readTree(response)
    if (result.path("action").asString() != "accept") return null
    return result
        .path("content")
        .path("city")
        .asString()
        .takeIf(String::isNotBlank)
}

private fun format(
    weather: WeatherObservation,
    units: String,
): String {
    val temperature =
        if (units == "celsius") {
            "%.1f°C".format(Locale.ROOT, weather.temperatureCelsius)
        } else {
            "%.1f°F".format(Locale.ROOT, weather.temperatureCelsius * 9 / 5 + 32)
        }
    return """
        Weather in ${weather.city}:
          Condition: ${weather.condition}
          Temperature: $temperature
          Humidity: ${weather.humidity}%
          Wind: ${"%.1f".format(weather.windSpeed)} km/h

        """.trimIndent()
}
