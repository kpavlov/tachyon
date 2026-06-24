/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.TachyonMcpServer;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

class ResourceCapabilitiesE2eTest extends AbstractMcpE2eTest {

    @Test
    void shouldListRegisteredResources() throws Exception {
        startEmptyServer();
        server.resources()
                .add(
                        ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain"),
                        (ctx, req) -> TextResourceContents.of("resource://doc", "text/plain", "Hello"))
                .add(
                        ResourceDescriptor.of("code", "resource://code", "Source code", "text/x-java"),
                        (ctx, req) ->
                                TextResourceContents.of("resource://code", "text/x-java", "package com.example;"));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
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
        server = TachyonMcpServer.builder().build();
        server.resources()
                .add(descriptor, (ctx, req) -> TextResourceContents.of("resource://doc", "text/plain", "Hello world"));
        startServer(server);

        var client = createTestClient();
        var sessionId = client.initialize();
        var response = client.sendRequest(sessionId, """
            {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://doc"}}
            """);

        assertThatJson(response.body()).inPath("$.result.contents[0].uri").isEqualTo("resource://doc");
        assertThatJson(response.body()).inPath("$.result.contents[0].text").isEqualTo("Hello world");
        assertThatJson(response.body()).inPath("$.result.contents[0].mimeType").isEqualTo("text/plain");
    }

    @Test
    void shouldReturnErrorForUnknownResource() throws Exception {
        startEmptyServer();

        var client = createTestClient();
        var sessionId = client.initialize();
        var response = client.sendRequest(sessionId, """
            {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://unknown"}}
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

    @Test
    void shouldReturnEmptyListWhenNoResources() throws Exception {
        startEmptyServer();

        var client = createTestClient();
        var sessionId = client.initialize();
        var response = client.sendRequest(sessionId, """
            {"jsonrpc":"2.0","id":2,"method":"resources/list"}
            """);

        assertThatJson(response.body()).inPath("$.result.resources").isArray().isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
        add-resource    | add
        remove-resource | remove
        """)
    void shouldNotifyListChanged(String toolName, String action) throws Exception {
        var builder = TachyonMcpServer.builder().tool(new NotifyListChangedToolHandler(action));
        if ("remove".equals(action)) {
            builder.resource(ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain"));
        }
        startServer(builder.build());

        var client = createTestClient();
        var sessionId = client.initialize();
        var response = client.sendRequest(sessionId, """
            {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"notify-list-changed","arguments":{}}}
            """);

        assertThat(response.body()).contains("notifications/resources/list_changed");
    }

    @Test
    void shouldNotifyResourceUpdated() throws Exception {
        startServer(TachyonMcpServer.builder()
                .resource(ResourceDescriptor.of("doc", "resource://doc", "A document", "text/plain"))
                .tool(new NotifyUpdatedToolHandler())
                .build());

        var client = createTestClient();
        var sessionId = client.initialize();

        var subResponse = client.sendRequest(sessionId, """
            {"jsonrpc":"2.0","id":2,"method":"resources/subscribe","params":{"uri":"resource://doc"}}
            """);
        assertThat(subResponse.statusCode()).isEqualTo(200);

        var response = client.sendRequest(sessionId, """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"notify-update","arguments":{}}}
            """);

        assertThat(response.body()).contains("notifications/resources/updated");
        assertThat(response.body()).contains("resource://doc");
    }

    // ---- Tool handler implementations ----

    private record NotifyListChangedToolHandler(String action) implements SyncToolHandler<Object, Object> {

        @Override
        public String name() {
            return "notify-list-changed";
        }

        @Override
        public String description() {
            return "Notifies resources/list_changed";
        }

        @Override
        public Object handle(McpContext context, Object arguments) {
            var resources = context.server().mcpServer().resources();
            if ("add".equals(action)) {
                resources.add(
                        ResourceDescriptor.of("added-resource", "resource://added", "Added by handler", "text/plain"),
                        (ctx, params) -> TextResourceContents.of("resource://added", "text/plain", "content"));
            } else {
                resources.remove("doc");
            }
            return ToolResult.of(List.of(TextContent.of("done")));
        }
    }

    private static class NotifyUpdatedToolHandler implements SyncToolHandler<Object, Object> {
        @Override
        public String name() {
            return "notify-update";
        }

        @Override
        public String description() {
            return "Triggers resource updated notification";
        }

        @Override
        public Object handle(McpContext context, Object arguments) {
            context.server().mcpServer().resources().notifyResourceUpdated("resource://doc");
            return ToolResult.of(List.of(TextContent.of("notified")));
        }
    }
}
