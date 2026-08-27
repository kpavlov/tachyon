/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather

import com.example.weather.service.WeatherService
import dev.tachyonmcp.core.server.TachyonServer
import dev.tachyonmcp.testkit.Mcp20260728Client
import dev.tachyonmcp.testkit.McpTestClients
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

// Redaction marker for fields kotest-assertions-json has no jsonunit-style
// "${json-unit.ignore-element}"/"${json-unit.regex}" placeholder for.
private const val IGNORED = "IGNORED"
private val IGNORED_KEYS = setOf("inputSchema", "outputSchema", "requestedSchema")
private val ICON_SRC_FIELD = Regex(""""src"\s*:\s*"data:image/png;base64,[^"]+"""")

private fun JsonElement.redactIgnoredKeys(): JsonElement =
    when (this) {
        is JsonObject -> {
            JsonObject(
                entries.associate { (key, value) ->
                    key to
                        if (key in
                            IGNORED_KEYS
                        ) {
                            JsonPrimitive(IGNORED)
                        } else {
                            value.redactIgnoredKeys()
                        }
                },
            )
        }

        is JsonArray -> {
            JsonArray(map { it.redactIgnoredKeys() })
        }

        else -> {
            this
        }
    }

// Redacts inputSchema/outputSchema/requestedSchema (structure not under test here) and
// icon `src` data URIs (fails the comparison if they don't look like base64 PNGs).
private fun String.redacted(): String =
    ICON_SRC_FIELD.replace(
        Json.parseToJsonElement(this).redactIgnoredKeys().toString(),
    ) { """"src":"$IGNORED"""" }

private const val CAPABILITIES = """{"tools":{},"resources":{},"prompts":{},"completions":{}}"""
private const val SERVER_INFO =
    """
    {
      "version": "1.0",
      "description": "Weather MCP server built with Tachyon Kotlin DSL",
      "websiteUrl": "https://github.com/kpavlov/tachyon/tree/main/examples/weather-mcp-kotlin",
      "name": "weather-server-kotlin",
      "title": "Weather Server (Kotlin)",
      "icons": [{"src": "$IGNORED", "mimeType": "image/png", "sizes": ["256x256"]}]
    }
    """
private const val RESOURCE_ANNOTATIONS =
    """{"audience": ["user", "assistant"], "priority": 0.8, "lastModified": "2026-07-23T00:00:00Z"}"""
private const val RESOURCE_ICON =
    """{"src": "$IGNORED", "mimeType": "image/png", "sizes": ["256x256"], "theme": "light"}"""

class WeatherServerTest {
    companion object {
        private val json = Json { prettyPrint = true }
        private val weatherProvider = TestWeatherProvider()
        private val cityProvider = TestCityProvider()
        private val weatherService = WeatherService(weatherProvider, cityProvider)
        private lateinit var handle: TachyonServer
        private lateinit var client: Mcp20260728Client

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            handle = createServer(0, weatherService)
            handle.start()
            client = McpTestClients.latest(handle.port())
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            client.close()
            handle.close()
        }

        private fun weatherCallResultBody(
            id: Int,
            city: String,
        ): String {
            val structured =
                """{"city":"$city","condition":"Clear sky","temperature":18.5,"temperatureUnit":"Celsius","humidity":52,"windSpeed":12.0}"""
            val textJson = json.encodeToString(structured)
            return """
                {
                  "jsonrpc": "2.0",
                  "id": $id,
                  "result": {
                    "content": [{"type": "text", "text": $textJson}],
                    "structuredContent": $structured,
                    "resultType": "complete"
                  }
                }
                """
        }

        private fun weatherResourceResultBody(
            id: Int,
            requestUri: String,
        ): String {
            val observation =
                """{"condition":"Clear sky","temperature":18.5,"temperatureUnit":"Celsius","humidity":52,"windSpeed":12.0}"""
            val textJson = json.encodeToString(observation)
            return """
                {
                  "jsonrpc": "2.0",
                  "id": $id,
                  "result": {
                    "contents": [{"text": $textJson, "uri": "$requestUri", "mimeType": "application/json"}],
                    "resultType": "complete",
                    "ttlMs": 0,
                    "cacheScope": "public"
                  }
                }
                """
        }
    }

    @Test
    fun verifyInitResult() {
        val response = client.post("""{"jsonrpc":"2.0","id":1,"method":"server/discover"}""")

        response.statusCode() shouldBe 200
        response.body().redacted() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "result": {
                "supportedVersions": ["2026-07-28", "2025-11-25"],
                "capabilities": $CAPABILITIES,
                "instructions": "Test instructions",
                "serverInfo": $SERVER_INFO,
                "_meta": {"io.modelcontextprotocol/serverInfo": $SERVER_INFO},
                "resultType": "complete",
                "ttlMs": 0,
                "cacheScope": "public"
              }
            }
            """
    }

    @Test
    fun `should list tools`() {
        val response = client.post("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")

        response.statusCode() shouldBe 200
        response.body().redacted() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "result": {
                "tools": [{
                  "description": "Get current weather for a city",
                  "inputSchema": "$IGNORED",
                  "outputSchema": "$IGNORED",
                  "annotations": {
                    "readOnlyHint": true, "destructiveHint": false, "idempotentHint": true, "openWorldHint": true
                  },
                  "name": "get-weather",
                  "title": "Current Weather",
                  "icons": [{"src": "$IGNORED", "mimeType": "image/png", "sizes": ["128x128"]}]
                }],
                "resultType": "complete",
                "ttlMs": 0,
                "cacheScope": "public"
              }
            }
            """
    }

    @Test
    fun `should call weather tool`() {
        val response =
            client.post(
                """
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"get-weather","arguments":{"city":"London","units":"Celsius"}}}
                """,
            )

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson weatherCallResultBody(3, "London")
    }

    @Test
    fun `should emit progress while fetching weather`() {
        client.clearNotifications()

        val responseBody =
            client.sendRpc(
                """
                {"jsonrpc":"2.0","id":4,"method":"tools/call",
                 "params":{"name":"get-weather","arguments":{"city":"London"},
                           "_meta":{"progressToken":"weather-progress"}}}
                """,
            )

        responseBody shouldEqualJson weatherCallResultBody(4, "London")

        val progressNotifications =
            client
                .notifications()
                .filter { it.method() == "notifications/progress" }
                .map { it.params() }
        progressNotifications shouldHaveSize 2
        progressNotifications[0].toString() shouldEqualJson
            """{"progressToken": "weather-progress", "progress": 0.1, "total": 1.0, "message": "Fetching weather for London"}"""
        progressNotifications[1].toString() shouldEqualJson
            """{"progressToken": "weather-progress", "progress": 1.0, "total": 1.0, "message": "Weather retrieved for London"}"""
    }

    @Test
    fun `should call weather tool after eliciting another city`() {
        val round1 =
            client.post(
                """
                {"jsonrpc":"2.0","id":5,"method":"tools/call",
                 "params":{"name":"get-weather","arguments":{"city":"Unknown"}}}
                """,
            )

        round1.statusCode() shouldBe 200
        round1.body().redacted() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 5,
              "result": {
                "inputRequests": {
                  "city": {
                    "method": "elicitation/create",
                    "params": {
                      "message": "City 'Unknown' was not found. Enter another city.",
                      "requestedSchema": "$IGNORED"
                    }
                  }
                },
                "resultType": "input_required"
              }
            }
            """

        val round2 =
            client.post(
                """
                {"jsonrpc":"2.0","id":6,"method":"tools/call",
                 "params":{"name":"get-weather","arguments":{"city":"Unknown"},
                           "inputResponses":{"city":{"city":"Tallinn"}}}}
                """,
            )

        round2.statusCode() shouldBe 200
        round2.body() shouldEqualJson weatherCallResultBody(6, "Tallinn")
    }

    @Test
    fun `should list resources`() {
        val response = client.post("""{"jsonrpc":"2.0","id":7,"method":"resources/list"}""")

        response.statusCode() shouldBe 200
        val size = weatherService.predictionArticle.toByteArray(Charsets.UTF_8).size
        response.body().redacted() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 7,
              "result": {
                "resources": [
                  {
                    "uri": "weather://featured/current",
                    "description": "Current weather in Tallinn",
                    "mimeType": "application/json",
                    "annotations": $RESOURCE_ANNOTATIONS,
                    "name": "featured-current-weather",
                    "title": "Featured Current Weather",
                    "icons": [$RESOURCE_ICON]
                  },
                  {
                    "uri": "weather://prediction/article",
                    "description": "Weather prediction article",
                    "mimeType": "text/markdown",
                    "annotations": $RESOURCE_ANNOTATIONS,
                    "size": $size,
                    "name": "prediction-article",
                    "title": "Weather Prediction",
                    "icons": [$RESOURCE_ICON]
                  }
                ],
                "resultType": "complete",
                "ttlMs": 0,
                "cacheScope": "public"
              }
            }
            """
    }

    @Test
    fun `should read text resource`() {
        val response =
            client.post(
                """{"jsonrpc":"2.0","id":8,"method":"resources/read","params":{"uri":"weather://prediction/article"}}""",
            )

        response.statusCode() shouldBe 200
        val articleJson = json.encodeToString(weatherService.predictionArticle)
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 8,
              "result": {
                "contents": [{"text": $articleJson, "uri": "weather://prediction/article", "mimeType": "text/markdown"}],
                "resultType": "complete",
                "ttlMs": 0,
                "cacheScope": "public"
              }
            }
            """
    }

    @Test
    fun `should read current weather resource`() {
        val response =
            client.post(
                """{"jsonrpc":"2.0","id":9,"method":"resources/read","params":{"uri":"weather://featured/current"}}""",
            )

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson weatherResourceResultBody(9, "weather://featured/current")
    }

    @Test
    fun `should list resource templates`() {
        val response =
            client.post(
                """
                {"jsonrpc":"2.0","id":10,"method":"resources/templates/list"}
                """.trimMargin(),
            )

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 10,
              "result": {
                "resourceTemplates": [{
                  "uriTemplate": "weather://current/{city}",
                  "description": "Weather forecast for a city",
                  "mimeType": "application/json",
                  "name": "current-weather",
                  "title": "Weather in the city"
                }],
                "resultType": "complete",
                "ttlMs": 0,
                "cacheScope": "public"
              }
            }
            """
    }

    @Test
    fun `should read current weather from template`() {
        val response =
            client.post(
                """{"jsonrpc":"2.0","id":11,"method":"resources/read","params":{"uri":"weather://current/London"}}""",
            )

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson weatherResourceResultBody(11, "weather://current/London")
    }

    @Test
    fun `should return invalid params when template city is unknown`() {
        val response =
            client.post(
                """{"jsonrpc":"2.0","id":12,"method":"resources/read","params":{"uri":"weather://current/Unknown"}}""",
            )

        response.statusCode() shouldBe 400
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 12,
              "error": {"code": -32602, "message": "invalid argument 'city': City not found: Unknown"}
            }
            """
    }

    @Test
    fun `should complete city name for current weather template`() {
        val response =
            client.post(
                """
                {"jsonrpc":"2.0","id":13,"method":"completion/complete",
                 "params":{"ref":{"type":"ref/resource","uri":"weather://current/{city}"},
                           "argument":{"name":"city","value":"Lo"}}}
                """,
            )

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 13,
              "result": {"completion": {"values": ["London", "Los Angeles"]}, "resultType": "complete"}
            }
            """
    }

    @Test
    fun `should return empty completion for blank query`() {
        val response =
            client.post(
                """
                {"jsonrpc":"2.0","id":14,"method":"completion/complete",
                 "params":{"ref":{"type":"ref/resource","uri":"weather://current/{city}"},
                           "argument":{"name":"city","value":""}}}
                """,
            )

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 14,
              "result": {"completion": {"values": []}, "resultType": "complete"}
            }
            """
    }

    @Test
    fun `should complete style name for rewrite-forecast prompt`() {
        val response =
            client.post(
                """
                {"jsonrpc":"2.0","id":15,"method":"completion/complete",
                 "params":{"ref":{"type":"ref/prompt","name":"rewrite-forecast"},
                           "argument":{"name":"style","value":"pi"}}}
                """,
            )

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 15,
              "result": {"completion": {"values": ["pirate"]}, "resultType": "complete"}
            }
            """
    }

    @Test
    fun `should return empty completion for non-style argument of rewrite-forecast prompt`() {
        val response =
            client.post(
                """
                {"jsonrpc":"2.0","id":16,"method":"completion/complete",
                 "params":{"ref":{"type":"ref/prompt","name":"rewrite-forecast"},
                           "argument":{"name":"forecast","value":"Rain"}}}
                """,
            )

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 16,
              "result": {"completion": {"values": []}, "resultType": "complete"}
            }
            """
    }

    @Test
    fun `should list prompts`() {
        val response = client.post("""{"jsonrpc":"2.0","id":17,"method":"prompts/list"}""")

        response.statusCode() shouldBe 200
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 17,
              "result": {
                "prompts": [{
                  "description": "Rewrites a weather forecast in a chosen style",
                  "arguments": [
                    {"description": "Weather forecast to rewrite", "required": true, "name": "forecast", "title": "Forecast"},
                    {"description": "plain, concise, or pirate", "required": true, "name": "style", "title": "Style"}
                  ],
                  "name": "rewrite-forecast"
                }],
                "resultType": "complete",
                "ttlMs": 0,
                "cacheScope": "public"
              }
            }
            """
    }

    @Test
    fun `should get prompt`() {
        val response =
            client.post(
                """
                {"jsonrpc":"2.0","id":18,"method":"prompts/get",
                 "params":{"name":"rewrite-forecast",
                           "arguments":{"forecast":"Rain in London","style":"pirate"}}}
                """,
            )

        response.statusCode() shouldBe 200
        val text =
            "Rewrite the following weather forecast in pirate style. " +
                "Preserve factual details:\n\n```Rain in London\n```"
        val textJson = json.encodeToString(text)
        response.body() shouldEqualJson
            """
            {
              "jsonrpc": "2.0",
              "id": 18,
              "result": {
                "description": "Rewrites a weather forecast in a chosen style",
                "messages": [{"role": "user", "content": {"type": "text", "text": $textJson}}],
                "resultType": "complete"
              }
            }
            """
    }
}
