/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.weather;

import com.example.weather.model.GetWeatherResponse;
import com.example.weather.model.TemperatureUnit;
import com.example.weather.service.WeatherService;
import com.example.weather.spi.WeatherObservation;
import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.McpTestClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

class WeatherServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ICON_SRC_PATTERN = "${json-unit.regex}^data:image/png;base64,.+$";
    private static final String CAPABILITIES = """
        {"tools":{},"resources":{},"prompts":{},"logging":{},"completions":{}}""";
    private static final String SERVER_INFO = """
        {
          "version": "1.0",
          "description": "Weather MCP server",
          "websiteUrl": "https://github.com/kpavlov/tachyon/tree/main/examples/weather-mcp",
          "name": "weather-server",
          "title": "Weather Server",
          "icons": [{"src": "%s", "mimeType": "image/png", "sizes": ["256x256"]}]
        }""".formatted(ICON_SRC_PATTERN);
    private static final String RESOURCE_ANNOTATIONS = """
        {"audience": ["user", "assistant"], "priority": 0.8, "lastModified": "2026-07-23T00:00:00Z"}""";
    private static final String RESOURCE_ICON = """
        {"src": "%s", "mimeType": "image/png", "sizes": ["256x256"], "theme": "light"}""".formatted(ICON_SRC_PATTERN);

    private static final TestWeatherProvider weatherProvider = new TestWeatherProvider();
    private static final TestCityProvider cityProvider = new TestCityProvider();
    private static final WeatherService weatherService = new WeatherService(weatherProvider, cityProvider);
    private static TachyonServer handle;
    private static Mcp20260728Client client;

    @BeforeAll
    static void beforeAll() {
        handle = WeatherServer.buildServer("localhost", 0, weatherService);
        handle.start();
        client = McpTestClients.latest(handle.port());
    }

    @AfterAll
    static void afterAll() {
        if (client != null) {
            client.close();
        }
        if (handle != null) {
            handle.close();
        }
    }

    private static String weatherCallResultBody(int id, String city, String unit) throws Exception {
        var response = new GetWeatherResponse(
            city, "Clear sky", 18.5, TemperatureUnit.valueOf(unit.toUpperCase(Locale.ROOT)), 52, 12.0);
        var structuredJson = MAPPER.writeValueAsString(response);
        var textJson = MAPPER.writeValueAsString(structuredJson);
        return """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "result": {
                "content": [{"type": "text", "text": %s}],
                "structuredContent": %s,
                "resultType": "complete"
              }
            }
            """.formatted(id, textJson, structuredJson);
    }

    @Test
    void shouldGetServerInfo() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":1,"method":"server/discover"}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "supportedVersions": ["2026-07-28", "2025-11-25"],
                    "capabilities": %s,
                    "instructions": "Test instructions",
                    "serverInfo": %s,
                    "_meta": {"io.modelcontextprotocol/serverInfo": %s},
                    "resultType": "complete",
                    "ttlMs": 0,
                    "cacheScope": "public"
                  }
                }
                """.formatted(CAPABILITIES, SERVER_INFO, SERVER_INFO));
    }

    @Test
    void shouldListTools() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "result": {
                    "tools": [{
                      "description": "Get current weather for a city",
                      "inputSchema": {
                        "$schema": "https://json-schema.org/draft/2020-12/schema",
                        "$id": "GetWeatherRequest",
                        "description": "Input for looking up the current weather in a city.",
                        "type": "object",
                        "required": ["city", "units"],
                        "additionalProperties": false,
                        "properties": {
                          "city": {"description": "City name (e.g., London, Tokyo, New York)", "type": "string"},
                          "units": {
                            "description": "Temperature unit (default: celsius)",
                            "oneOf": [{"type": "null"}, {"$ref": "#/$defs/TemperatureUnit"}]
                          }
                        },
                        "$defs": {
                          "TemperatureUnit": {
                            "type": "string",
                            "description": "Unit used to represent temperature.",
                            "enum": ["celsius", "fahrenheit"]
                          }
                        }
                      },
                      "outputSchema": {
                        "$schema": "https://json-schema.org/draft/2020-12/schema",
                        "$id": "GetWeatherResponse",
                        "description": "Current weather observation for a city.",
                        "type": "object",
                        "required": ["city", "condition", "temperature", "unit", "humidity", "windSpeed"],
                        "additionalProperties": false,
                        "properties": {
                          "city": {"description": "City name", "type": "string"},
                          "condition": {"description": "Weather condition", "type": "string"},
                          "temperature": {"description": "Temperature in the response unit", "type": "number"},
                          "unit": {"description": "Temperature unit", "$ref": "#/$defs/TemperatureUnit"},
                          "humidity": {"description": "Relative humidity percentage", "type": "integer"},
                          "windSpeed": {"description": "Wind speed in km/h", "type": "number"}
                        },
                        "$defs": {
                          "TemperatureUnit": {
                            "type": "string",
                            "description": "Unit used to represent temperature.",
                            "enum": ["celsius", "fahrenheit"]
                          }
                        }
                      },
                      "annotations": {
                        "readOnlyHint": true, "destructiveHint": false, "idempotentHint": true, "openWorldHint": true
                      },
                      "name": "get-weather",
                      "title": "Current Weather",
                      "icons": [{"src": "%s", "mimeType": "image/png", "sizes": ["128x128"]}]
                    }],
                    "resultType": "complete",
                    "ttlMs": 0,
                    "cacheScope": "public"
                  }
                }
                """.formatted(ICON_SRC_PATTERN));
    }

    @Test
    void shouldCallWeatherTool() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":3,"method":"tools/call",
             "params":{"name":"get-weather","arguments":{"city":"London","units":"celsius"}}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body()).isEqualTo(weatherCallResultBody(3, "London", "celsius"));
    }

    @Test
    void shouldEmitProgressWhileFetchingWeather() throws Exception {
        client.clearNotifications();

        var response = client.post("""
            {"jsonrpc":"2.0","id":4,"method":"tools/call",
             "params":{"name":"get-weather","arguments":{"city":"London","units":null},
                       "_meta":{"progressToken":"weather-progress"}}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body()).isEqualTo(weatherCallResultBody(4, "London", "celsius"));

        var progressNotifications = client.notifications().stream()
            .filter(n -> n.method().equals("notifications/progress"))
            .map(n -> n.params())
            .toList();
        assertThat(progressNotifications).hasSize(2);
        assertThatJson(progressNotifications.get(0).toString())
            .isEqualTo("""
                {"progressToken": "weather-progress", "progress": 0.1, "total": 1.0, "message": "Fetching weather for London"}
                """);
        assertThatJson(progressNotifications.get(1).toString())
            .isEqualTo("""
                {"progressToken": "weather-progress", "progress": 1.0, "total": 1.0, "message": "Weather retrieved for London"}
                """);
    }

    @Test
    void shouldCallWeatherToolAfterElicitingAnotherCity() throws Exception {
        var round1 = client.post("""
            {"jsonrpc":"2.0","id":5,"method":"tools/call",
             "params":{"name":"get-weather","arguments":{"city":"Unknown","units":null}}}
            """);

        assertThat(round1.statusCode()).isEqualTo(200);
        assertThatJson(round1.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 5,
                  "result": {
                    "inputRequests": {
                      "city": {
                        "method": "elicitation/create",
                        "params": {
                          "message": "City 'Unknown' was not found. Enter another city.",
                          "requestedSchema": "${json-unit.ignore-element}"
                        }
                      }
                    },
                    "resultType": "input_required"
                  }
                }
                """);

        var round2 = client.post("""
            {"jsonrpc":"2.0","id":6,"method":"tools/call",
             "params":{"name":"get-weather","arguments":{"city":"Unknown","units":null},
                       "inputResponses":{"city":{"city":"Tallinn"}}}}
            """);

        assertThat(round2.statusCode()).isEqualTo(200);
        assertThatJson(round2.body()).isEqualTo(weatherCallResultBody(6, "Tallinn", "celsius"));
    }

    @Test
    void shouldListResources() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":7,"method":"resources/list"}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        var size = weatherService.predictionArticle().getBytes(StandardCharsets.UTF_8).length;
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 7,
                  "result": {
                    "resources": [
                      {
                        "uri": "weather://prediction/article",
                        "description": "Weather prediction article",
                        "mimeType": "text/markdown",
                        "annotations": %s,
                        "size": %d,
                        "name": "prediction-article",
                        "title": "Weather Prediction",
                        "icons": [%s]
                      },
                      {
                        "uri": "weather://featured/current",
                        "description": "Current weather in Tallinn",
                        "mimeType": "application/json",
                        "annotations": %s,
                        "name": "featured-current-weather",
                        "title": "Featured Current Weather",
                        "icons": [%s]
                      }
                    ],
                    "resultType": "complete",
                    "ttlMs": 0,
                    "cacheScope": "public"
                  }
                }
                """.formatted(RESOURCE_ANNOTATIONS, size, RESOURCE_ICON, RESOURCE_ANNOTATIONS, RESOURCE_ICON));
    }

    @Test
    void shouldReadTextResource() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":8,"method":"resources/read",
             "params":{"uri":"weather://prediction/article"}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        var articleJson = MAPPER.writeValueAsString(weatherService.predictionArticle());
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 8,
                  "result": {
                    "contents": [{"text": %s, "uri": "weather://prediction/article", "mimeType": "text/markdown"}],
                    "resultType": "complete",
                    "ttlMs": 0,
                    "cacheScope": "public"
                  }
                }
                """.formatted(articleJson));
    }

    private static String weatherResourceResultBody(int id, String requestUri) throws Exception {
        var weather = new WeatherObservation("Clear sky", 18.5, 52, 12.0);
        var textJson = MAPPER.writeValueAsString(MAPPER.writeValueAsString(weather));
        return """
            {
              "jsonrpc": "2.0",
              "id": %d,
              "result": {
                "contents": [{"text": %s, "uri": "%s", "mimeType": "application/json"}],
                "resultType": "complete",
                "ttlMs": 0,
                "cacheScope": "public"
              }
            }
            """.formatted(id, textJson, requestUri);
    }

    @Test
    void shouldReadCurrentWeatherResource() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":9,"method":"resources/read",
             "params":{"uri":"weather://featured/current"}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body()).isEqualTo(weatherResourceResultBody(9, "weather://featured/current"));
    }

    @Test
    void shouldListResourceTemplates() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":10,"method":"resources/templates/list"}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body())
            .isEqualTo("""
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
                """);
    }

    @Test
    void shouldReadCurrentWeatherFromTemplate() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":11,"method":"resources/read",
             "params":{"uri":"weather://current/London"}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body()).isEqualTo(weatherResourceResultBody(11, "weather://current/London"));
    }

    @Test
    void shouldReturnInvalidParamsWhenTemplateCityIsUnknown() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":12,"method":"resources/read",
             "params":{"uri":"weather://current/Unknown"}}
            """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 12,
                  "error": {"code": -32602, "message": "invalid argument 'city': City not found: Unknown"}
                }
                """);
    }

    @Test
    void shouldCompleteCityNameForCurrentWeatherTemplate() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":13,"method":"completion/complete",
             "params":{"ref":{"type":"ref/resource","uri":"weather://current/{city}"},
                       "argument":{"name":"city","value":"Lo"}}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 13,
                  "result": {"completion": {"values": ["London", "Los Angeles"]}, "resultType": "complete"}
                }
                """);
    }

    @Test
    void shouldReturnEmptyCompletionForBlankQuery() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":14,"method":"completion/complete",
             "params":{"ref":{"type":"ref/resource","uri":"weather://current/{city}"},
                       "argument":{"name":"city","value":""}}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 14,
                  "result": {"completion": {"values": []}, "resultType": "complete"}
                }
                """);
    }

    @Test
    void shouldCompleteStyleNameForRewriteForecastPrompt() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":15,"method":"completion/complete",
             "params":{"ref":{"type":"ref/prompt","name":"rewrite-forecast"},
                       "argument":{"name":"style","value":"pi"}}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 15,
                  "result": {"completion": {"values": ["pirate"]}, "resultType": "complete"}
                }
                """);
    }

    @Test
    void shouldReturnEmptyCompletionForNonStyleArgumentOfRewriteForecastPrompt() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":16,"method":"completion/complete",
             "params":{"ref":{"type":"ref/prompt","name":"rewrite-forecast"},
                       "argument":{"name":"forecast","value":"Rain"}}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 16,
                  "result": {"completion": {"values": []}, "resultType": "complete"}
                }
                """);
    }

    @Test
    void shouldListPrompts() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":17,"method":"prompts/list"}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThatJson(response.body())
            .isEqualTo("""
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
                """);
    }

    @Test
    void shouldGetPrompt() throws Exception {
        var response = client.post("""
            {"jsonrpc":"2.0","id":18,"method":"prompts/get",
             "params":{"name":"rewrite-forecast",
                       "arguments":{"forecast":"Rain in London","style":"pirate"}}}
            """);

        assertThat(response.statusCode()).isEqualTo(200);
        var text = "Rewrite the following weather forecast in pirate style. "
            + "Preserve factual details:\n\n```Rain in London\n```";
        var textJson = MAPPER.writeValueAsString(text);
        assertThatJson(response.body())
            .isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 18,
                  "result": {
                    "messages": [{"role": "user", "content": {"type": "text", "text": %s}}],
                    "resultType": "complete"
                  }
                }
                """.formatted(textJson));
    }
}
