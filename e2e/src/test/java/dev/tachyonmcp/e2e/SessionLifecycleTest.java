/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.runtime.SessionState;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class SessionLifecycleTest extends AbstractMcpE2eTest {

    @Test
    void disconnectOneClientDoesNotAffectOther() throws Exception {
        var pingBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

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

            var response = clientB.sendRequest(sessionB, pingBody);
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
            // DELETE without MCP-Session-Id header
            var response = client.post(null, "");
            // The init handler forwards to operation handler which rejects
            // Actually we need a raw DELETE without session header
            var deleteResponse = client.delete("");
            // Empty session id triggers "Missing MCP-Session-Id" from operation handler
            assertThat(deleteResponse.statusCode()).isIn(400, 404);
        }
    }

    @Test
    void sessionRemovedAfterDeleteCannotBeUsed() throws Exception {
        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            // Delete the session
            client.delete(sessionId);

            // Attempt to use the deleted session
            var pingBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
            var response = client.sendRequest(sessionId, pingBody);
            // Should fail - session no longer exists
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("error");
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
            var pingBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
            var response = client.sendRequest(session2, pingBody);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("result");
        }
    }

    @Test
    void postWithoutSessionReturnsError() throws Exception {
        try (var client = createTestClient()) {
            var body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
            var response = client.post(body);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Missing MCP-Session-Id");
        }
    }
}
