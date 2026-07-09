/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceTemplateEntry;
import org.junit.jupiter.api.Test;

class ResourceTemplateTest extends AbstractMcpE2eTest {

    @Test
    void shouldReadResourceFromSingleParamTemplate() throws Exception {
        startEmptyServer();
        server.resources()
                .addTemplate(ResourceTemplateEntry.of(
                        "item",
                        "resource://items/{id}",
                        "An item",
                        "text/plain",
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "item=" + params.get("id"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://items/42"}}
                """);

            // language=JSON
            assertThatJson(response.body()).inPath("$.result").isEqualTo("""
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
                .addTemplate(ResourceTemplateEntry.of(
                        "items",
                        "resource://items/{id}",
                        "An item",
                        "text/plain",
                        (ctx, uri, params) -> TextResourceContents.of(uri, "text/plain", "item=" + params.get("id"))))
                .addTemplate(ResourceTemplateEntry.of(
                        "orders",
                        "resource://orders/{orderId}",
                        "An order",
                        "text/plain",
                        (ctx, uri, params) ->
                                TextResourceContents.of(uri, "text/plain", "order=" + params.get("orderId"))))
                .addTemplate(ResourceTemplateEntry.of(
                        "users",
                        "resource://users/{userId}",
                        "A user",
                        "text/plain",
                        (ctx, uri, params) ->
                                TextResourceContents.of(uri, "text/plain", "user=" + params.get("userId"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var r1 = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://orders/99"}}
                """);
            // language=JSON
            assertThatJson(r1.body()).inPath("$.result").isEqualTo("""
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
            // language=JSON
            assertThatJson(r2.body()).inPath("$.result").isEqualTo("""
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
                .addTemplate(ResourceTemplateEntry.of(
                        "user-post",
                        "users://{userId}/posts/{postId}",
                        "A user post",
                        "text/plain",
                        (ctx, uri, params) -> TextResourceContents.of(
                                uri, "text/plain", "user=" + params.get("userId") + ",post=" + params.get("postId"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"users://alice/posts/42"}}
                """);

            // language=JSON
            assertThatJson(response.body()).inPath("$.result").isEqualTo("""
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
