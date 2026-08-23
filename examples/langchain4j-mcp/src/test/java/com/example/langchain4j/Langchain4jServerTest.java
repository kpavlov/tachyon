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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Langchain4jServerTest {

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

        assertThat(result.tools()).hasSize(1);
        McpSchema.Tool tool = result.tools().getFirst();
        assertThat(tool.name()).isEqualTo("placeOrder");
        assertThat(tool.description())
            .isEqualTo("Places an order for a customer and returns a confirmation with computed totals");
        assertThat(tool.inputSchema()).isEqualTo(Map.of(
            "type", "object",
            "properties", Map.of(
                "request", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "customer", Map.of("type", "string"),
                        "items", Map.of(
                            "type", "array",
                            "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                    "name", Map.of("type", "string"),
                                    "quantity", Map.of("type", "integer")),
                                "required", List.of("name", "quantity")))),
                    "required", List.of("customer", "items"))),
            "required", List.of("request")));
    }

    @Test
    void placeOrderAcceptsCompositeRequestAndReturnsCompositeConfirmation() {
        final var result = client.callTool(McpSchema.CallToolRequest.builder("placeOrder")
            .arguments(Map.of(
                "request",
                Map.of(
                    "customer", "Ada",
                    "items",
                    List.of(
                        Map.of("name", "Widget", "quantity", 2),
                        Map.of("name", "Gadget", "quantity", 3)))))
            .build());

        assertThat(result.isError()).isNotEqualTo(true);
        assertThat(result.structuredContent())
            .isEqualTo(Map.of(
                "orderId", "ORD-ADA-5",
                "customer", "Ada",
                "totalItems", 5,
                "totalPrice", 49.95));
    }
}
