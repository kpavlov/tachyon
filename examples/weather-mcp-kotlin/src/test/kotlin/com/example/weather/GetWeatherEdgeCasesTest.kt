/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather

import com.example.weather.service.WeatherService
import com.example.weather.spi.WeatherProvider
import dev.tachyonmcp.testkit.McpTestClients
import io.kotest.assertions.json.shouldEqualJson
import org.junit.jupiter.api.Test
import java.io.IOException

class GetWeatherEdgeCasesTest {
    private fun callGetWeather(
        weatherService: WeatherService,
        secondRoundInputResponses: String,
    ): String {
        val server = createServer(0, weatherService)
        server.start()
        val client = McpTestClients.latest(server.port())
        try {
            val round1 =
                client.post(
                    """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"get-weather","arguments":{"city":"Unknown"}}}
                    """,
                )
            val body = round1.body()
            if (!body.contains("\"resultType\":\"input_required\"")) {
                return body
            }
            val round2 =
                client.post(
                    """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call",
                     "params":{"name":"get-weather","arguments":{"city":"Unknown"},
                               "inputResponses":$secondRoundInputResponses}}
                    """,
                )
            return round2.body()
        } finally {
            client.close()
            server.close()
        }
    }

    @Test
    fun `returns city not found when elicitation is cancelled`() {
        val body =
            callGetWeather(
                WeatherService(
                    weatherProvider = TestWeatherProvider(),
                    cityProvider = TestCityProvider(),
                ),
                """{"city":{}}""",
            )

        body shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "result": {
                "content": [{"type": "text", "text": "City not found"}],
                "isError": true,
                "resultType": "complete"
              }
            }
            """
    }

    @Test
    fun `returns city not found when the elicited city is unknown`() {
        val body =
            callGetWeather(
                WeatherService(TestWeatherProvider(), TestCityProvider()),
                """{"city":{"city":"Unknown"}}""",
            )

        body shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "result": {
                "content": [{"type": "text", "text": "City not found"}],
                "isError": true,
                "resultType": "complete"
              }
            }
            """
    }

    @Test
    fun `returns fixed error when provider fails without leaking details`() {
        val failingProvider =
            WeatherProvider { _, _ ->
                throw IOException("connection refused to internal-host:6443")
            }

        val body =
            callGetWeather(
                WeatherService(
                    weatherProvider = failingProvider,
                    cityProvider = TestCityProvider(),
                ),
                """{"city":{}}""",
            )

        body shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {
                "content": [{"type": "text", "text": "Could not get weather"}],
                "isError": true,
                "resultType": "complete"
              }
            }
            """
    }
}
