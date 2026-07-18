/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SessionState;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class StatefulSessionLifecycleTest extends AbstractStatefulMcpE2eTest {

    @Test
    void statefulLifecycleIssuesAndReusesSessionId() throws Exception {
        try (var client = createTestClient()) {
            // MCP Streamable HTTP: reuse the issued session ID on every subsequent request.
            var sessionId = client.initialize();

            var response = client.sendRpc("""
                    {"jsonrpc":"2.0","id":2,"method":"ping"}
                    """);

            assertThat(sessionId).isNotBlank();
            assertThat(engine().getSession(sessionId))
                    .isPresent()
                    .map(Session::state)
                    .hasValue(SessionState.ACTIVE);
            // language=JSON
            assertThatJson(response).isEqualTo("""
                    {"jsonrpc":"2.0","id":2,"result":{}}
                    """);
        }
    }

    @Test
    void disconnectOneClientDoesNotAffectOther() throws Exception {
        try (var clientA = createTestClient();
                var clientB = createTestClient()) {
            var sessionA = clientA.initialize();
            var sessionB = clientB.initialize();

            clientA.delete(sessionA);

            assertThat(engine().getSession(sessionA)).isEmpty();

            assertThat(engine().getSession(sessionB))
                    .isPresent()
                    .map(Session::state)
                    .hasValue(SessionState.ACTIVE);

            var response = clientB.ping(sessionB, 1);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("result");
        }
    }

    @Test
    void deleteTerminatesSession() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            assertThat(engine().getSession(sessionId))
                    .isPresent()
                    .map(Session::state)
                    .hasValue(SessionState.ACTIVE);

            HttpResponse<String> response = client.delete(sessionId);
            assertThat(response.statusCode()).isEqualTo(200);

            // Session should be removed from server
            assertThat(engine().getSession(sessionId)).isEmpty();
        }
    }

    @Test
    void deleteReturns400WithoutSessionHeader() throws Exception {
        try (var client = createTestClient()) {
            // MCP Streamable HTTP: DELETE without MCP-Session-Id header returns 400.
            var deleteResponse = client.delete("");
            assertThat(deleteResponse.statusCode()).isEqualTo(400);
        }
    }

    @Test
    void postWithDeletedSessionReturns404() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            client.delete(sessionId);

            // MCP Streamable HTTP: a terminated session ID returns HTTP 404.
            var response = client.ping(sessionId, 1);
            assertThat(response.statusCode()).isEqualTo(404);
        }
    }

    @Test
    void multipleSessionsAreIndependent() throws Exception {
        try (var client = createTestClient()) {
            var session1 = client.initialize();
            var session2 = client.initialize();

            // Delete session1
            client.delete(session1);
            assertThat(engine().getSession(session1)).isEmpty();

            // session2 should still be active
            assertThat(engine().getSession(session2))
                    .isPresent()
                    .map(Session::state)
                    .hasValue(SessionState.ACTIVE);

            // session2 should still work
            var response = client.ping(session2, 1);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("result");
        }
    }

    @Test
    void postWithoutSessionReturnsError() throws Exception {
        try (var client = createTestClient()) {
            // MCP Streamable HTTP: a non-initialize POST without MCP-Session-Id returns a raw
            // HTTP 400, not a JSON-RPC error envelope.
            var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
            var response = client.post(body);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("Missing MCP-Session-Id");
            assertThat(response.body()).doesNotContain("jsonrpc");
        }
    }

    @Test
    void notificationWithoutSessionReturnsError() throws Exception {
        try (var client = createTestClient()) {
            client.initialize();

            var response = client.post("""
                    {"jsonrpc":"2.0","method":"notifications/initialized"}
                    """);

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).isEqualTo("Missing MCP-Session-Id header");
        }
    }
}
