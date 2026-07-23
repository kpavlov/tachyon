// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather

import com.example.weather.integration.OpenMeteoProvider
import com.example.weather.service.NarrationStyle
import com.example.weather.service.WeatherService
import com.example.weather.spi.CityNotFoundException
import com.example.weather.spi.WeatherObservation
import dev.tachyonmcp.server.TachyonServer
import dev.tachyonmcp.server.domain.Annotations
import dev.tachyonmcp.server.domain.Icon
import dev.tachyonmcp.server.domain.InvalidArgumentException
import dev.tachyonmcp.server.domain.PromptArgument
import dev.tachyonmcp.server.domain.PromptMessage
import dev.tachyonmcp.server.domain.Role
import dev.tachyonmcp.server.domain.TextResourceContents
import dev.tachyonmcp.server.features.completions.CompletionResult
import dev.tachyonmcp.server.features.prompts.PromptDescriptor
import dev.tachyonmcp.server.json.JsonSchemaUtils
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration
import java.util.Base64
import java.util.Locale
import java.util.concurrent.Executors

private val log = LoggerFactory.getLogger("com.example.weather.WeatherServer")
private val MAPPER = ObjectMapper()
private val LOGO by lazy { classpathDataUri("/images/logo.png", "image/png") }

fun main() {
    val server = createServer(8080)
    log.info("Connect your MCP client to http://localhost:{}/mcp", server.port())
}

fun createWeatherService(): WeatherService {
    val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build()
    val openMeteoProvider = OpenMeteoProvider(httpClient)
    return WeatherService(openMeteoProvider, openMeteoProvider)
}

fun createServer(
    port: Int,
    weatherService: WeatherService = createWeatherService(),
): TachyonServer {
    val predictionArticle = weatherService.predictionArticle
    val resourceAnnotations =
        Annotations {
            audience = listOf(Role.USER, Role.ASSISTANT)
            priority = 0.8
            lastModified = "2026-07-23T00:00:00Z"
        }
    val resourceIcon =
        Icon {
            src = LOGO
            mimeType = "image/png"
            sizes = listOf("256x256")
            theme = "light"
        }
    return TachyonServer(port = port) {
        info {
            name = "weather-server-kotlin"
            title = "Weather Server (Kotlin)"
            description = "Weather MCP server built with Tachyon Kotlin DSL"
            websiteUrl = "https://github.com/kpavlov/tachyon/tree/main/examples/weather-mcp-kotlin"
            instructions = "Test instructions"
            icons += Icon(LOGO, "image/png", listOf("256x256"))
            version = "1.0"
        }
        session { enabled = true }

        tool(getWeatherToolDescriptor()) { getWeather(weatherService) }

        resource(
            name = "prediction-article",
            uri = "weather://prediction/article",
            description = "Weather prediction article",
            mimeType = "text/markdown",
            title = "Weather Prediction",
            annotations = resourceAnnotations,
            size = predictionArticle.toByteArray().size.toLong(),
            icons = listOf(resourceIcon),
        ) {
            TextResourceContents { text = predictionArticle }
        }

        resource(
            name = "featured-current-weather",
            uri = "weather://featured/current",
            description = "Current weather in Tallinn",
            mimeType = "application/json",
            title = "Featured Current Weather",
            annotations = resourceAnnotations,
            icons = listOf(resourceIcon),
        ) {
            TextResourceContents { text = asJson(weatherService.currentWeather("Tallinn")) }
        }

        prompt(rewriteForecastPromptDescriptor()) { rewriteForecast(weatherService, arguments) }

        promptCompletion("rewrite-forecast") { completeStyle(request.argumentName(), request.argumentValue()) }

        resourceTemplate(
            name = "current-weather",
            uriTemplate = "weather://current/{city}",
            title = "Weather in the city",
            description = "Weather forecast for a city",
            mimeType = "application/json",
        ) {
            TextResourceContents { text = handleWeatherTemplate(weatherService, param("city")) }
        }

        resourceCompletion("weather://current/{city}") {
            if (request.argumentName() != "city") {
                CompletionResult.of(emptyList())
            } else {
                CompletionResult.of(weatherService.searchCities(request.argumentValue()))
            }
        }
    }
}

private fun rewriteForecastPromptDescriptor(): PromptDescriptor =
    PromptDescriptor {
        name = "rewrite-forecast"
        description = "Rewrites a weather forecast in a chosen style"
        arguments =
            listOf(
                PromptArgument.of("forecast", "Forecast", "Weather forecast to rewrite", true),
                PromptArgument.of("style", "Style", "plain, concise, or pirate", true),
            )
        inputSchema = JsonSchemaUtils.parseSchema(NarrationStyle.inputSchema())
    }

private fun rewriteForecast(
    weatherService: WeatherService,
    argumentsJson: String?,
): List<PromptMessage> {
    val arguments = MAPPER.readTree(argumentsJson ?: "{}")
    val forecast = arguments.path("forecast").asString()
    val style = NarrationStyle.from(arguments.path("style").asString())
    return listOf(PromptMessage.user(weatherService.rewriteForecastInstruction(forecast, style)))
}

private fun completeStyle(
    argumentName: String,
    argumentValue: String,
): CompletionResult {
    if (argumentName != "style") return CompletionResult.of(emptyList())
    val query = argumentValue.lowercase(Locale.ROOT)
    val matches = NarrationStyle.styleNames().filter { it.startsWith(query) }
    return CompletionResult.of(matches)
}

private fun handleWeatherTemplate(
    weatherService: WeatherService,
    city: String,
): String =
    try {
        asJson(weatherService.currentWeather(city))
    } catch (e: CityNotFoundException) {
        throw InvalidArgumentException("city", e.message ?: "City not found: $city", e)
    } catch (e: Exception) {
        restoreInterruptStatus(e)
        throw IllegalStateException("Could not get weather", e)
    }

private fun asJson(weather: WeatherObservation): String =
    try {
        MAPPER.writeValueAsString(weather)
    } catch (e: Exception) {
        throw IllegalStateException("Could not serialize weather", e)
    }

private object ClasspathResources

private fun classpathDataUri(
    path: String,
    mimeType: String,
): String {
    val bytes =
        ClasspathResources.javaClass.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("Missing classpath resource: $path")
    return "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
}
