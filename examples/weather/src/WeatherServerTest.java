/* Copyright (c) 2026 Konstantin Pavlov. */

package com.example.weather;

import dev.tachyonmcp.server.McpServerHandle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

class WeatherServerTest {

    private static McpServerHandle handle;
    private static int port;

    @BeforeAll
    static void beforeAll() {
        handle = WeatherServer.createServer(0);
        port = handle.port();
    }

    @AfterAll
    static void afterAll() {
        handle.close();
    }

    @Test
    void shouldCallWeatherToolAndReturnJsonWithCityAndCondition() throws Exception {
        try (var client = new WeatherTestClient(port)) {
            var sessionId = client.initialize();

            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":1,"method":"tools/call",
                 "params":{"name":"get-weather","arguments":{"city":"London","units":"celsius"}}}
                """);

            var json = response.body();
            assertThatJson(json).node("result.content[0].type").isEqualTo("text");
            assertThatJson(json).node("result.content[0].text").isString()
                .contains("London")
                .contains("Condition:")
                .contains("Temperature:");
        }
    }

    @Test
    void shouldListTools() throws Exception {
        try (var client = new WeatherTestClient(port)) {
            var sessionId = client.initialize();

            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                """);

            var json = response.body();
            assertThatJson(json).isEqualTo(
                // language=json
                """
                    {"jsonrpc":"2.0",
                    "id":1,
                    "result":{
                      "tools":[
                        {
                          "name":"get-weather",
                          "description":"Get current weather for a city",
                          "inputSchema":{"type":"object","properties":{"city":{"type":"string","description":"City name (e.g., London, Tokyo, New York)"},"units":{"type":"string","enum":["celsius","fahrenheit"],"description":"Temperature unit (default: celsius)"}},"required":["city"]}
                          }]}
                    }
                    """);
        }
    }

    @Test
    void shouldListAndReadResources() throws Exception {
        try (var client = new WeatherTestClient(port)) {
            var sessionId = client.initialize();

            var listResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":1,"method":"resources/list"}
                """);
            var listJson = listResponse.body();
            assertThatJson(listJson).node("result.resources[0].name").isEqualTo("current-weather-image");
            assertThatJson(listJson).node("result.resources[1].name").isEqualTo("prediction-article");
            assertThatJson(listJson).node("result.resources[1].uri").isEqualTo("weather://prediction/article");

            var readResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read",
                 "params":{"uri":"weather://prediction/article"}}
                """);
            var readJson = readResponse.body();
            assertThatJson(readJson).node("result.contents[0].mimeType").isEqualTo("text/markdown");
            assertThatJson(readJson).node("result.contents[0].text").isString().contains("Weather Prediction");
        }
    }
}
