/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2026_07_28;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.mcp.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.McpTestClients;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 server-stateless (SEP-2575): {@code server/discover} advertising a capability
 * must be backed by a real, dispatchable handler for that capability with no session — a
 * regression test that {@code tools/list} (and any other capability {@code discover} advertises)
 * actually works statelessly, not just {@code server/discover} itself. This is the same
 * `discover-capabilities-match-handlers` check from `server-stateless`; it only ever failed
 * because {@code tools/list} was rejected for lack of a session (see {@code StatelessDispatchTest}).
 */
class DiscoverCapabilitiesMatchHandlersTest extends AbstractStatelessMcpE2eTest<Mcp20260728Client> {

    @Override
    protected Mcp20260728Client createTestClient() {
        return createTestClient(port);
    }

    @Override
    protected Mcp20260728Client createTestClient(int port) {
        return McpTestClients.latest(port);
    }

    @Test
    void toolsCapabilityAdvertisedInDiscoverActuallyWorks() throws Exception {
        try (var client = createModernTestClient()) {
            client.discover().isSuccess().hasCapabilities("""
                    {"logging":{},"tools":{}}
                    """);

            var toolsList = client.post("""
                    {"jsonrpc": "2.0", "id": 2, "method": "tools/list"}
                    """);
            assertThat(toolsList.statusCode()).as(toolsList.body()).isEqualTo(200);
            assertThatJson(toolsList.body()).inPath("$.result.tools").isArray();
        }
    }
}
