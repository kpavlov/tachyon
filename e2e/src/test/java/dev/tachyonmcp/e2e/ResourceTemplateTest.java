/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.server.domain.TextResourceContents;
import org.junit.jupiter.api.Test;

class ResourceTemplateTest extends AbstractStatelessMcpE2eTest {

    @Test
    void shouldReadResourceFromSingleParamTemplate() throws Exception {
        startEmptyServer();
        server.resources()
                .registerTemplate(
                        builder -> builder.name("item")
                                .uriTemplate("resource://items/{id}")
                                .description("An item")
                                .mimeType("text/plain"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(
                                rawUri, "text/plain", "item=" + params.get("id").scalarValue()));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://items/42"}}
                """);

            assertThatJson(response.body())
                    .inPath("$.result")
                    .isEqualTo(
                            // language=JSON
                            """
                {
                  "contents": [
                    {
                      "uri": "resource://items/42",
                      "mimeType": "text/plain",
                      "text": "item=42"
                    }
                  ]
                }
                """);
        }
    }

    @Test
    void shouldMatchCorrectTemplateWhenMultipleRegistered() throws Exception {
        startEmptyServer();
        server.resources()
                .registerTemplate(
                        builder -> builder.name("items")
                                .uriTemplate("resource://items/{id}")
                                .description("An item")
                                .mimeType("text/plain"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(
                                rawUri, "text/plain", "item=" + params.get("id").scalarValue()))
                .registerTemplate(
                        builder -> builder.name("orders")
                                .uriTemplate("resource://orders/{orderId}")
                                .description("An order")
                                .mimeType("text/plain"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(
                                rawUri,
                                "text/plain",
                                "order=" + params.get("orderId").scalarValue()))
                .registerTemplate(
                        builder -> builder.name("users")
                                .uriTemplate("resource://users/{userId}")
                                .description("A user")
                                .mimeType("text/plain"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(
                                rawUri,
                                "text/plain",
                                "user=" + params.get("userId").scalarValue()));

        try (var client = createTestClient()) {
            client.initialize();

            var r1 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://orders/99"}}
                """);
            assertThatJson(r1.body())
                    .inPath("$.result")
                    .isEqualTo(
                            // language=JSON
                            """
                {
                  "contents": [
                    {
                      "uri": "resource://orders/99",
                      "mimeType": "text/plain",
                      "text": "order=99"
                    }
                  ]
                }
                """);

            var r2 = client.post("""
                {"jsonrpc":"2.0","id":3,"method":"resources/read","params":{"uri":"resource://users/alice"}}
                """);
            assertThatJson(r2.body())
                    .inPath("$.result")
                    .isEqualTo(
                            // language=JSON
                            """
                {
                  "contents": [
                    {
                      "uri": "resource://users/alice",
                      "mimeType": "text/plain",
                      "text": "user=alice"
                    }
                  ]
                }
                """);

            var r3 = client.post("""
                {"jsonrpc":"2.0","id":4,"method":"resources/read","params":{"uri":"resource://items/7"}}
                """);
            assertThatJson(r3.body())
                    .inPath("$.result")
                    .isEqualTo(
                            // language=JSON
                            """
                {
                  "contents": [
                    {
                      "uri": "resource://items/7",
                      "mimeType": "text/plain",
                      "text": "item=7"
                    }
                  ]
                }
                """);
        }
    }

    @Test
    void shouldReadResourceFromMultiParamTemplate() throws Exception {
        startEmptyServer();
        server.resources()
                .registerTemplate(
                        builder -> builder.name("user-post")
                                .uriTemplate("users://{userId}/posts/{postId}")
                                .description("User's post")
                                .mimeType("text/plain"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(
                                rawUri,
                                "text/plain",
                                "user=" + params.get("userId").scalarValue() + ",post="
                                        + params.get("postId").scalarValue()));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"users://alice/posts/42"}}
                """);

            assertThatJson(response.body())
                    .inPath("$.result")
                    .isEqualTo(
                            // language=JSON
                            """
                {
                  "contents": [
                    {
                      "uri": "users://alice/posts/42",
                      "mimeType": "text/plain",
                      "text": "user=alice,post=42"
                    }
                  ]
                }
                """);
        }
    }

    @Test
    void shouldListRegisteredTemplates() throws Exception {
        startEmptyServer();
        server.resources()
                .registerTemplate(
                        builder -> builder.name("item")
                                .uriTemplate("resource://items/{id}")
                                .description("An item")
                                .mimeType("text/plain"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(
                                rawUri, "text/plain", "item=" + params.get("id").scalarValue()));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/templates/list"}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.resourceTemplates")
                    .isArray()
                    .isEqualTo(
                            // language=JSON
                            """
                        [
                          {
                            "name": "item",
                            "uriTemplate": "resource://items/{id}",
                            "description": "An item",
                            "mimeType": "text/plain"
                          }
                        ]
                        """);
        }
    }

    @Test
    void shouldRejectInvalidTemplateCursor() throws Exception {
        startEmptyServer();
        server.resources()
                .registerTemplate(
                        builder -> builder.name("item").uriTemplate("resource://items/{id}"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(rawUri, "text/plain", "item"));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/templates/list","params":{"cursor":"invalid"}}
                """);

            assertThatJson(response.body()).isEqualTo("""
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "error": {
                    "code": -32602,
                    "message": "Invalid cursor"
                  }
                }
                """);
        }
    }

    @Test
    void shouldReturnErrorWhenUriMatchesNoRegisteredTemplate() throws Exception {
        startEmptyServer();
        server.resources()
                .registerTemplate(
                        builder -> builder.name("item")
                                .uriTemplate("resource://items/{id}")
                                .description("An item")
                                .mimeType("text/plain"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(
                                rawUri, "text/plain", "item=" + params.get("id").scalarValue()));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://orders/99"}}
                """);

            // language=JSON
            var expected = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "error": {
                    "code": -32002,
                    "message": "Resource not found"
                  }
                }
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }
}
