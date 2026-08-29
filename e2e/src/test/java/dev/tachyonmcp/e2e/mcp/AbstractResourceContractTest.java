/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.testkit.McpClient;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;

/**
 * {@code resources/list}/{@code resources/read} success and error paths that hold under both MCP
 * protocol revisions: only the transport handshake and the {@code resources/read} not-found error
 * code differ, supplied by subclasses in {@code v2025_11_25}/{@code v2026_07_28}. Subscribe/notify
 * scenarios stay version-specific (2026-07-28 replaced {@code resources/subscribe} with
 * {@code subscriptions/listen}) and are not part of this contract.
 */
public abstract class AbstractResourceContractTest extends AbstractStatelessMcpE2eTest {

    /** Returns a client ready to send requests (handshake already performed, if the version needs one). */
    protected abstract McpClient readyClient() throws Exception;

    /** The wire error code for {@code resources/read} on an unknown URI. */
    protected abstract int resourceNotFoundErrorCode();

    @Test
    void shouldListRegisteredResources() throws Exception {
        startEmptyServer();
        server.resources()
                .register(
                        r -> r.name("doc")
                                .uri("resource://doc")
                                .description("A document")
                                .mimeType("text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "Hello", "text/plain"))
                .register(
                        r -> r.name("code")
                                .uri("resource://code")
                                .description("Source code")
                                .mimeType("text/x-java"),
                        (ctx, request) ->
                                TextResourceContents.of("resource://code", "package com.example;", "text/x-java"));

        try (var client = readyClient()) {
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"resources/list"}
                """);

            // registration order is irrelevant: resources/list sorts by name.
            assertThatJson(response.body())
                    .when(Option.IGNORING_EXTRA_FIELDS)
                    .isEqualTo(
                            // language=JSON
                            """
                            {
                              "result": {
                                "resources": [
                                  {"name": "code", "uri": "resource://code"},
                                  {"name": "doc", "uri": "resource://doc"}
                                ]
                              }
                            }
                            """);
        }
    }

    @Test
    void shouldReturnEmptyListWhenNoResources() throws Exception {
        startEmptyServer();

        try (var client = readyClient()) {
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"resources/list"}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.resources")
                    .isArray()
                    .isEmpty();
        }
    }

    @Test
    void shouldReadTextResource() throws Exception {
        startEmptyServer();
        server.resources()
                .register(
                        r -> r.name("doc")
                                .uri("resource://doc")
                                .description("A document")
                                .mimeType("text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "Hello world", "text/plain"));

        try (var client = readyClient()) {
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://doc"}}
                """);

            assertThatJson(response.body())
                    .when(Option.IGNORING_EXTRA_FIELDS)
                    .isEqualTo(
                            // language=JSON
                            """
                            {
                              "result": {
                                "contents": [
                                  {"uri": "resource://doc", "mimeType": "text/plain", "text": "Hello world"}
                                ]
                              }
                            }
                            """);
        }
    }

    @Test
    void shouldReadCorrectResourceWhenMultipleRegistered() throws Exception {
        startEmptyServer();
        server.resources()
                .register(
                        r -> r.name("alpha")
                                .uri("resource://alpha")
                                .description("Alpha")
                                .mimeType("text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "content-alpha", "text/plain"))
                .register(
                        r -> r.name("beta")
                                .uri("resource://beta")
                                .description("Beta")
                                .mimeType("text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "content-beta", "text/plain"))
                .register(
                        r -> r.name("gamma")
                                .uri("resource://gamma")
                                .description("Gamma")
                                .mimeType("text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "content-gamma", "text/plain"));

        try (var client = readyClient()) {
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://beta"}}
                """);

            assertThatJson(response.body())
                    .when(Option.IGNORING_EXTRA_FIELDS)
                    .isEqualTo(
                            // language=JSON
                            """
                {
                  "result": {
                    "contents": [
                      {
                        "uri": "resource://beta",
                        "mimeType": "text/plain",
                        "text": "content-beta"
                      }
                    ]
                  }
                }
                """);
        }
    }

    @Test
    void shouldReturnErrorForUnknownResource() throws Exception {
        startEmptyServer();

        try (var client = readyClient()) {
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"resource://unknown"}}
                """);

            // language=JSON
            var expected = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "error": {
                    "code": %d,
                    "message": "Resource not found",
                    "data":{"uri":"resource://unknown"}
                  }
                }
                """.formatted(resourceNotFoundErrorCode());
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }
}
