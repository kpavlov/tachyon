/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.AsyncResourceHandler;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

class ResourceTest extends AbstractStatefulMcpE2eTest {

    @Test
    void shouldListRegisteredResources() throws Exception {
        startEmptyServer();
        server.resources()
                .register(
                        ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "Hello", "text/plain"))
                .register(
                        ResourceDescriptor.of("code", "resource://code", "Source code", "text/x-java"),
                        (ctx, request) ->
                                TextResourceContents.of("resource://code", "package com.example;", "text/x-java"));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/list"}
                """);

            var mapper = new ObjectMapper();
            var root = mapper.readTree(response.body());
            var resources = root.at("/result/resources");
            assertThat(resources).isNotNull();
            assertThat(resources.size()).isEqualTo(2);
            assertThat(resources.get(0).get("name").asString()).isEqualTo("code");
            assertThat(resources.get(0).get("uri").asString()).isEqualTo("resource://code");
            assertThat(resources.get(1).get("name").asString()).isEqualTo("doc");
            assertThat(resources.get(1).get("uri").asString()).isEqualTo("resource://doc");
        }
    }

    @Test
    void shouldReadTextResource() throws Exception {
        var descriptor = ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain");
        startEmptyServer();
        server.resources()
                .register(
                        descriptor,
                        (ctx, request) -> TextResourceContents.of(request.uri(), "Hello world", "text/plain"));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://doc"}}
                """);

            assertThatJson(response.body()).inPath("$.result.contents[0].uri").isEqualTo("resource://doc");
            assertThatJson(response.body()).inPath("$.result.contents[0].text").isEqualTo("Hello world");
            assertThatJson(response.body())
                    .inPath("$.result.contents[0].mimeType")
                    .isEqualTo("text/plain");
        }
    }

    @Test
    void shouldRedactIllegalArgumentExceptionFromInvalidParamsError() throws Exception {
        startEmptyServer();
        server.resources()
                .register(
                        ResourceDescriptor.of("bad-arg", "resource://bad-arg", null, "text/plain"), (ctx, request) -> {
                            throw new IllegalArgumentException("sensitive internal detail");
                        });

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://bad-arg"}}
                """);
            // language=JSON
            var expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "error": {"code": -32602, "message": "Invalid params"}
                    }
                    """;
            assertThatJson(response).isEqualTo(expected);
            assertThat(response).doesNotContain("sensitive internal detail");
        }
    }

    @Test
    void shouldReadCorrectResourceWhenMultipleRegistered() throws Exception {
        startEmptyServer();
        server.resources()
                .register(
                        ResourceDescriptor.of("alpha", "resource://alpha", "Alpha", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "content-alpha", "text/plain"))
                .register(
                        ResourceDescriptor.of("beta", "resource://beta", "Beta", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "content-beta", "text/plain"))
                .register(
                        ResourceDescriptor.of("gamma", "resource://gamma", "Gamma", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "content-gamma", "text/plain"));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://beta"}}
                """);

            assertThatJson(response.body())
                    .inPath("$.result")
                    .isEqualTo(
                            // language=JSON
                            """
                {
                  "contents": [
                    {
                      "uri": "resource://beta",
                      "mimeType": "text/plain",
                      "text": "content-beta"
                    }
                  ]
                }
                """);
        }
    }

    @Test
    void shouldReadResourceFromAsyncHandler() throws Exception {
        var descriptor = ResourceDescriptor.of("async-doc", "resource://async-doc", "Async document", "text/plain");
        AsyncResourceHandler handler = (ctx, request) -> CompletableFuture.supplyAsync(
                () -> TextResourceContents.of("resource://async-doc", "async content", "text/plain"));
        startEmptyServer();
        server.resources().register(descriptor, handler);

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://async-doc"}}
                """);

            assertThatJson(response.body()).inPath("$.result.contents[0].uri").isEqualTo("resource://async-doc");
            assertThatJson(response.body()).inPath("$.result.contents[0].text").isEqualTo("async content");
        }
    }

    @Test
    void shouldPassMetaToResourceHandler() throws Exception {
        startServer(builder -> builder.resource(
                resource -> resource.name("meta-doc").uri("resource://meta-doc"),
                (ctx, request) -> TextResourceContents.of(
                        request.uri(), request.meta().get("tenant").asString(), "text/plain")));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{
                  "uri":"resource://meta-doc",
                  "_meta":{"tenant":"acme"}
                }}
                """);

            assertThatJson(response.body())
                    .isEqualTo(
                            // language=JSON
                            """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "contents": [
                          {
                            "uri": "resource://meta-doc",
                            "mimeType": "text/plain",
                            "text": "acme"
                          }
                        ]
                      }
                    }
                    """);
        }
    }

    @Test
    void shouldReturnErrorWhenAsyncHandlerFails() throws Exception {
        var descriptor = ResourceDescriptor.of("failing", "resource://failing", "Failing resource", "text/plain");
        AsyncResourceHandler handler =
                (ctx, request) -> CompletableFuture.failedFuture(new IllegalStateException("backend down"));
        startEmptyServer();
        server.resources().register(descriptor, handler);

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://failing"}}
                """);

            assertThatJson(response.body()).inPath("$.error.message").isEqualTo("Resource handler failed");
        }
    }

    @Test
    void shouldReturnErrorForUnknownResource() throws Exception {
        startEmptyServer();

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://unknown"}}
                """);

            // language=JSON
            var expected = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "error": {
                    "code": -32002,
                    "message": "Resource not found",
                    "data":{"uri":"resource://unknown"}
                  }
                }
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void shouldReturnEmptyListWhenNoResources() throws Exception {
        startEmptyServer();

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/list"}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.resources")
                    .isArray()
                    .isEmpty();
        }
    }

    @Test
    void shouldUnregisterResourceByUriAndFailRead() throws Exception {
        startEmptyServer();
        server.resources()
                .register(
                        ResourceDescriptor.of("alpha", "resource://alpha", "Alpha", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "content-alpha", "text/plain"))
                .register(
                        ResourceDescriptor.of("beta", "resource://beta", "Beta", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "content-beta", "text/plain"));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            server.resources().unregisterByUri("resource://alpha");

            var readResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://alpha"}}
                """);
            assertThatJson(readResponse.body()).inPath("$.error.code").isEqualTo(-32002);

            var listResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"resources/list"}
                """);
            assertThatJson(listResponse.body()).inPath("$.result.resources");
            assertThatJson(listResponse.body())
                    .inPath("$.result.resources[0].name")
                    .isEqualTo("beta");
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
        add-resource    | add
        remove-resource | remove
        """)
    void shouldNotifyListChanged(String toolName, String action) throws Exception {
        startServer(builder -> {
            builder.capabilities(c -> c.resourcesListChanged(true)).tool(notifyListChangedTool(action));
            if ("remove".equals(action)) {
                builder.resource(
                        ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "", "text/plain"));
            }
        });

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"notify-list-changed","arguments":{}}}
                """);

            assertThat(response.body()).contains("notifications/resources/list_changed");
        }
    }

    @Test
    void shouldNotifyResourceUpdated() throws Exception {
        startServer(it -> it.resource(
                        ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "", "text/plain"))
                .tool(notifyUpdatedTool()));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var subResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/subscribe","params":{"uri":"resource://doc"}}
                """);
            assertThat(subResponse.statusCode()).isEqualTo(200);

            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"notify-update","arguments":{}}}
                """);

            assertThat(response.body()).contains("notifications/resources/updated");
            assertThat(response.body()).contains("resource://doc");
        }
    }

    @Test
    void shouldNotNotifyAfterUnsubscribe() throws Exception {
        startServer(it -> it.resource(
                        ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "", "text/plain"))
                .tool(notifyUpdatedTool()));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var subResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/subscribe","params":{"uri":"resource://doc"}}
                """);
            assertThat(subResponse.statusCode()).isEqualTo(200);

            var unsubResponse = client.post(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"resources/unsubscribe","params":{"uri":"resource://doc"}}
                """);
            assertThat(unsubResponse.statusCode()).isEqualTo(200);

            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"notify-update","arguments":{}}}
                """);

            assertThat(response.body()).doesNotContain("notifications/resources/updated");
        }
    }

    @Test
    void shouldNotifyListChangedOnUnregisterByUri() throws Exception {
        startServer(builder -> {
            builder.capabilities(c -> c.resourcesListChanged(true)).tool(unregisterByUriTool());
            builder.resource(
                    ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain"),
                    (ctx, request) -> TextResourceContents.of(request.uri(), "", "text/plain"));
        });

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"unregister-by-uri","arguments":{}}}
                """);

            assertThat(response.body()).contains("notifications/resources/list_changed");
        }
    }

    // ---- Tool handler implementations ----

    private ToolHandler notifyListChangedTool(String action) {
        return ToolHandler.of(
                b -> b.name("notify-list-changed").description("Notifies resources/list_changed"),
                (context, request) -> {
                    var resources = server.resources();
                    if ("add".equals(action)) {
                        resources.register(
                                ResourceDescriptor.of(
                                        "added-resource", "resource://added", "Added by handler", "text/plain"),
                                (ctx, resourceRequest) ->
                                        TextResourceContents.of(resourceRequest.uri(), "content", "text/plain"));
                    } else {
                        resources.unregister("doc");
                    }
                    return ToolResult.blocks(TextContent.of("done"));
                });
    }

    private ToolHandler notifyUpdatedTool() {
        return ToolHandler.of(
                b -> b.name("notify-update").description("Triggers resource updated notification"),
                (context, request) -> {
                    server.resources().notifyResourceUpdated("resource://doc");
                    return ToolResult.blocks(TextContent.of("resource update 'resource://doc' notified "));
                });
    }

    private ToolHandler unregisterByUriTool() {
        return ToolHandler.of(
                b -> b.name("unregister-by-uri").description("Unregisters resource by URI"), (context, request) -> {
                    server.resources().unregisterByUri("resource://doc");
                    return ToolResult.blocks(TextContent.of("done"));
                });
    }
}
