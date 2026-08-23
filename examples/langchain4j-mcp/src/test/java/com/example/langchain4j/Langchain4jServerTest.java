/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.langchain4j;

import dev.tachyonmcp.core.server.TachyonServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

class Langchain4jServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static TachyonServer server;
    private static HttpClientStreamableHttpTransport clientTransport;
    private static McpSyncClient client;

    @BeforeAll
    static void beforeAll() {
        server = Langchain4jServer.buildServer(0, new OrderService());
        server.start();

        clientTransport = HttpClientStreamableHttpTransport.builder("http://localhost:" + server.port())
            .build();
        client = McpClient.sync(clientTransport).build();
        client.initialize();
    }

    @AfterAll
    static void afterAll() {
        client.close();
        server.close();
    }

    @Test
    void listsPlaceOrderToolWithCompositeInputSchema() {
        final var result = client.listTools();

        // language=JSON
        var expected = """
            {
              "tools": [
                {
                  "name": "placeOrder",
                  "description": "Places an order for a customer and returns a confirmation with computed totals",
                  "inputSchema": {
                    "type": "object",
                    "properties": {
                      "request": {
                        "type": "object",
                        "properties": {
                          "customer": { "type": "string" },
                          "items": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "properties": {
                                "name": { "type": "string" },
                                "quantity": { "type": "integer" }
                              },
                              "required": ["name", "quantity"]
                            }
                          }
                        },
                        "required": ["customer", "items"]
                      }
                    },
                    "required": ["request"]
                  }
                }
              ]
            }
            """;
        assertThatJson(JSON.writeValueAsString(result)).isEqualTo(expected);
    }

    @Test
    void placeOrderAcceptsCompositeRequestAndReturnsCompositeConfirmation() {
        final var result = client.callTool(McpSchema.CallToolRequest.builder("placeOrder")
            .arguments(Map.of(
                "request",
                Map.of(
                    "customer", "Ada",
                    "items",
                    java.util.List.of(
                        Map.of("name", "Widget", "quantity", 2),
                        Map.of("name", "Gadget", "quantity", 3)))))
            .build());

        // language=JSON
        var expected = """
            {
              "content": [
                {
                  "type": "text",
                  "text": "{\\"orderId\\":\\"ORD-ADA-5\\",\\"customer\\":\\"Ada\\",\\"totalItems\\":5,\\"totalPrice\\":49.95}"
                }
              ],
              "structuredContent": {
                "orderId": "ORD-ADA-5",
                "customer": "Ada",
                "totalItems": 5,
                "totalPrice": 49.95
              }
            }
            """;
        assertThatJson(JSON.writeValueAsString(result)).isEqualTo(expected);
    }
}
