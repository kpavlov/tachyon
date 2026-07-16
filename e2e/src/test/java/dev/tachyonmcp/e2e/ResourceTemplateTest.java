/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceTemplateTest extends AbstractMcpE2eTest {

    private static String scalar(Map<String, UriTemplateValue> params, String name) {
        return ((UriTemplateValue.Scalar) params.get(name)).value();
    }

    @Test
    void shouldReadResourceFromSingleParamTemplate() throws Exception {
        startEmptyServer();
        server.resources()
                .registerTemplate(
                        builder -> builder.name("item")
                                .uriTemplate("resource://items/{id}")
                                .description("An item")
                                .mimeType("text/plain"),
                        (ctx, uri, params) ->
                                TextResourceContents.of(uri, "text/plain", "item=" + scalar(params, "id")));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
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
                        (ctx, uri, params) ->
                                TextResourceContents.of(uri, "text/plain", "item=" + scalar(params, "id")))
                .registerTemplate(
                        builder -> builder.name("orders")
                                .uriTemplate("resource://orders/{orderId}")
                                .description("An order")
                                .mimeType("text/plain"),
                        (ctx, uri, params) ->
                                TextResourceContents.of(uri, "text/plain", "order=" + scalar(params, "orderId")))
                .registerTemplate(
                        builder -> builder.name("users")
                                .uriTemplate("resource://users/{userId}")
                                .description("A user")
                                .mimeType("text/plain"),
                        (ctx, uri, params) ->
                                TextResourceContents.of(uri, "text/plain", "user=" + scalar(params, "userId")));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var r1 = client.sendRequest(sessionId, """
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

            var r2 = client.sendRequest(sessionId, """
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
                        (ctx, uri, params) -> TextResourceContents.of(
                                uri,
                                "text/plain",
                                "user=" + scalar(params, "userId") + ",post=" + scalar(params, "postId")));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
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
}
