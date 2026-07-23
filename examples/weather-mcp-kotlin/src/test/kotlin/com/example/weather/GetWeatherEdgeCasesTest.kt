// Copyright (c) 2026 Konstantin Pavlov and contributors.

package com.example.weather

import com.example.weather.service.WeatherService
import com.example.weather.spi.WeatherProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.*
import org.junit.jupiter.api.Test
import java.io.IOException

class GetWeatherEdgeCasesTest {
    private fun callGetWeather(
        weatherService: WeatherService,
        elicitationResponse: (McpSchema.ElicitRequest) -> McpSchema.ElicitResult,
    ): McpSchema.CallToolResult {
        val server = createServer(0, weatherService)
        val transport = HttpClientStreamableHttpTransport.builder("http://localhost:${server.port()}").build()
        val client = McpClient.sync(transport).elicitation(elicitationResponse).build()
        try {
            client.initialize()
            return client.callTool(
                McpSchema.CallToolRequest
                    .builder("get-weather")
                    .arguments(mapOf("city" to "Unknown"))
                    .build(),
            )
        } finally {
            client.close()
            transport.close()
            server.close()
        }
    }

    @Test
    fun `returns city not found when elicitation is cancelled`() {
        val result =
            callGetWeather(WeatherService(TestWeatherProvider(), TestCityProvider())) {
                McpSchema.ElicitResult(Action.CANCEL, null)
            }

        val content = result.content().first()
        content.shouldBeInstanceOf<McpSchema.TextContent>()
        content.text() shouldBe "City not found"
        result.isError shouldBe true
    }

    @Test
    fun `returns city not found when the elicited city is unknown`() {
        val result =
            callGetWeather(WeatherService(TestWeatherProvider(), TestCityProvider())) {
                McpSchema.ElicitResult(Action.ACCEPT, mapOf("city" to "Unknown"))
            }

        val content = result.content().first()
        content.shouldBeInstanceOf<McpSchema.TextContent>()
        content.text() shouldBe "City not found"
        result.isError shouldBe true
    }

    @Test
    fun `returns fixed error when provider fails without leaking details`() {
        val failingProvider =
            WeatherProvider { throw IOException("connection refused to internal-host:6443") }

        val result =
            callGetWeather(WeatherService(failingProvider, TestCityProvider())) {
                McpSchema.ElicitResult(Action.CANCEL, null)
            }

        val content = result.content().first()
        content.shouldBeInstanceOf<McpSchema.TextContent>()
        content.text() shouldBe "Could not get weather"
        result.isError shouldBe true
    }
}
