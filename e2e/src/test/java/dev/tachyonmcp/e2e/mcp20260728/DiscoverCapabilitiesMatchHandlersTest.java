/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 server-stateless (SEP-2575): {@code server/discover} advertising a capability
 * must be backed by a real, dispatchable handler for that capability with no session — a
 * regression test that {@code tools/list} (and any other capability {@code discover} advertises)
 * actually works statelessly, not just {@code server/discover} itself. This is the same
 * `discover-capabilities-match-handlers` check from `server-stateless`; it only ever failed
 * because {@code tools/list} was rejected for lack of a session (see {@code StatelessDispatchTest}).
 */
class DiscoverCapabilitiesMatchHandlersTest extends AbstractStatelessMcpE2eTest {

    @Test
    void toolsCapabilityAdvertisedInDiscoverActuallyWorks() throws Exception {
        try (var client = createModernTestClient()) {
            var discover = client.post("""
                    {"jsonrpc": "2.0", "id": 1, "method": "server/discover"}
                    """);
            assertThat(discover.statusCode()).as(discover.body()).isEqualTo(200);
            assertThatJson(discover.body())
                    .inPath("$.result.capabilities.tools")
                    .isPresent();

            var toolsList = client.post("""
                    {"jsonrpc": "2.0", "id": 2, "method": "tools/list"}
                    """);
            assertThat(toolsList.statusCode()).as(toolsList.body()).isEqualTo(200);
            assertThatJson(toolsList.body()).inPath("$.result.tools").isArray();
        }
    }
}
