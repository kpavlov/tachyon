// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather

import com.example.weather.model.GetWeatherInput
import com.example.weather.model.TemperatureUnit
import com.example.weather.model.TemperatureUnit.Celsius
import com.example.weather.model.TemperatureUnit.Fahrenheit
import com.example.weather.service.WeatherService
import com.example.weather.spi.CityNotFoundException
import com.example.weather.spi.WeatherObservation
import dev.tachyonmcp.api.json.JsonSchema
import dev.tachyonmcp.api.runtime.ElicitationRequest
import dev.tachyonmcp.api.runtime.ElicitationResult
import dev.tachyonmcp.api.runtime.InteractionContext
import dev.tachyonmcp.api.server.domain.ProgressToken
import dev.tachyonmcp.api.server.features.tools.ToolResult
import dev.tachyonmcp.kotlin.server.config.ToolScope
import dev.tachyonmcp.kotlin.server.domain.stringOrNull
import dev.tachyonmcp.kotlin.server.features.tools.ToolDescriptor
import me.kpavlov.kt.schema.generator.json.JsonSchemaConfig
import me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val log = LoggerFactory.getLogger("com.example.weather.GetWeatherTool")
private const val ELICITATION_TIMEOUT_SECONDS = 600L

private val schemaGenerator =
    ReflectionClassJsonSchemaGenerator(
        json = kotlinx.serialization.json.Json { encodeDefaults = false },
        config = JsonSchemaConfig.Default,
    )

private val CITY_SCHEMA =
    JsonSchema.parse(
        schemaGenerator.generateSchemaString(CityElicitationInput::class),
    )

private data class CityElicitationInput(val city: String)

val getWeatherToolDescriptor =
    ToolDescriptor {
        name = "get-weather"
        title = "Current Weather"
        description = "Get current weather for a city"
        inputSchema(schemaGenerator.generateSchemaString(GetWeatherInput::class))
        outputSchema(schemaGenerator.generateSchemaString(WeatherObservation::class))
    }

fun ToolScope.getWeather(weatherService: WeatherService): ToolResult {
    val args = request.arguments()
    val city = args.stringValue("city")
    val units = args.stringOrNull("units")
    val progressToken = request.progressToken()
    val temperatureUnit = when (units?.lowercase(Locale.getDefault())) {
        "celsius" -> Celsius
        "fahrenheit" -> Fahrenheit
        else -> {
            Celsius
        }
    }

    fun attempt(city: String): ToolResult =
        try {
            ToolResult.structured(
                fetchWithProgress(
                    ctx, progressToken, weatherService, city, temperatureUnit
                )
            )
        } catch (e: Exception) {
            if (e is CityNotFoundException) throw e
            internalError(e)
        }

    return try {
        attempt(city)
    } catch (_: CityNotFoundException) {
        val elicitedCity = elicitCity(ctx, city) ?: return ToolResult.error("City not found")
        try {
            attempt(elicitedCity)
        } catch (_: CityNotFoundException) {
            ToolResult.error("City not found")
        }
    }
}

private fun fetchWithProgress(
    ctx: InteractionContext,
    progressToken: ProgressToken?,
    weatherService: WeatherService,
    city: String,
    temperatureUnit: TemperatureUnit,
): WeatherObservation {
    ctx.notifications().progress(progressToken, 0.1, 1.0, "Fetching weather for $city")
    val weather = weatherService.currentWeather(city, temperatureUnit)
    ctx.notifications()
        .progress(progressToken, 1.0, 1.0, "Weather retrieved for $city")
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
        ctx.client().elicitation().create(
            ElicitationRequest("City '$city' was not found. Enter another city.", CITY_SCHEMA),
        )
    val result =
        try {
            future.get(ELICITATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            restoreInterruptStatus(e)
            throw e
        } catch (_: TimeoutException) {
            return null
        }
    if (result.action() != ElicitationResult.Action.ACCEPT) return null
    return result.content()?.stringOr("city", "")?.takeIf(String::isNotBlank)
}

private fun format(
    weather: WeatherObservation,
): String {
    val temperature =
        when (weather.temperatureUnit) {
            Celsius -> {
                "%.1f°C".format(Locale.ROOT, weather.temperature)
            }

            Fahrenheit -> {
                "%.1f°F".format(Locale.ROOT, weather.temperature * 9 / 5 + 32)
            }
        }
    return """
        Weather in ${weather.city}:
          Condition: ${weather.condition}
          Temperature: $temperature
          Humidity: ${weather.humidity}%
          Wind: ${"%.1f".format(weather.windSpeed)} km/h

        """.trimIndent()
}
