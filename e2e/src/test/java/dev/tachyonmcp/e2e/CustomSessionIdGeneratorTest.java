/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.ServerHandle;
import dev.tachyonmcp.server.TachyonServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Verifies a custom {@link dev.tachyonmcp.server.session.SessionIdGenerator} derives the session id
 * from an incoming request header, and that the derived id is usable for follow-up requests.
 *
 * @author Konstantin Pavlov
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomSessionIdGeneratorTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private ServerHandle serverHandle;
    private int port;

    @BeforeAll
    void beforeAll() {
        serverHandle = TachyonServer.builder()
                .tool(EchoToolHandler.create())
                .session(s -> s.enabled(true)
                        .sessionIdGenerator(
                                request -> "tenant-" + request.headers().get(TENANT_HEADER)))
                .network(n -> n.host("localhost").port(0))
                .start();
        port = serverHandle.port();
    }

    @AfterAll
    void afterAll() {
        serverHandle.close();
    }

    @Test
    void shouldDeriveSessionIdFromRequestHeaderAndReuseIt() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var init = post(
                    client,
                    "acme",
                    null,
                    // language=JSON
                    """
                    {"jsonrpc":"2.0","id":1,"method":"initialize",
                     "params":{"protocolVersion":"2025-11-25","capabilities":{},
                               "clientInfo":{"name":"test","version":"1.0"}}}
                    """);

            assertThat(init.statusCode()).isEqualTo(200);
            assertThat(init.headers().firstValue("MCP-Session-Id")).hasValue("tenant-acme");

            // Activate the session, then reuse the derived id for a real request.
            post(client, null, "tenant-acme", """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);

            var toolsList = post(client, null, "tenant-acme", """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);

            assertThat(toolsList.statusCode()).isEqualTo(200);
            assertThatJson(toolsList.body()).inPath("$.result.tools").isArray().isNotEmpty();
        }
    }

    @Test
    void shouldFallBackToDefaultGeneratorWhenCustomGeneratorThrows() throws Exception {
        var failingHandle = TachyonServer.builder()
                .session(s -> s.enabled(true).sessionIdGenerator(request -> {
                    throw new IllegalStateException("boom");
                }))
                .network(n -> n.host("localhost").port(0))
                .start();
        try (var client = HttpClient.newHttpClient()) {
            var init = post(
                    client,
                    failingHandle.port(),
                    null,
                    null,
                    // language=JSON
                    """
                    {"jsonrpc":"2.0","id":33,"method":"initialize",
                     "params":{"protocolVersion":"2025-11-25","capabilities":{},
                               "clientInfo":{"name":"test","version":"1.0"}}}
                    """);

            assertThat(init.statusCode()).isEqualTo(200);
            JsonAssertions.assertThatJson(init.body())
                    .isEqualTo(
                            // language=JSON
                            """
                 {"jsonrpc":"2.0","id":33,"error":{"code":-32603,"message":"Internal error"}}
                """);
        } finally {
            failingHandle.close();
        }
    }

    private HttpResponse<String> post(
            HttpClient client, @Nullable String tenantId, @Nullable String sessionId, String body) throws Exception {
        return post(client, port, tenantId, sessionId, body);
    }

    private HttpResponse<String> post(
            HttpClient client, int targetPort, @Nullable String tenantId, @Nullable String sessionId, String body)
            throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + targetPort + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-11-25")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (tenantId != null) {
            builder.header(TENANT_HEADER, tenantId);
        }
        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
