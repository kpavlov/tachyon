/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP resource-not-found errors (sep-2164): the error {@code data} field SHOULD include the
 * requested {@code uri}. {@code DefaultResourceRegistry.ResourcesReadHandler} already threads the
 * uri through {@code ServerErrors.resourceNotFound(detail, Map.of("uri", uri))} — this only ever
 * failed under 2026-07-28 because the session gate rejected the request before reaching that code.
 */
class ResourceNotFoundTest extends AbstractStatelessMcpE2eTest {

    @BeforeEach
    void registerFixtures() {
        startServer(b -> b.capabilities(c -> c.resources()));
    }

    @Test
    void resourceNotFoundIncludesRequestedUri() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 1, "method": "resources/read", "params": {"uri": "test://does-not-exist"}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32602);
            assertThatJson(response.body()).inPath("$.error.data.uri").isEqualTo("test://does-not-exist");
        }
    }
}
