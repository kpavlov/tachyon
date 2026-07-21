/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 (SEP-2575): logging is per-request, not session-scoped. A server MUST NOT send
 * {@code notifications/message} for a request that did not set {@code _meta.../logLevel} — there
 * is no {@code logging/setLevel} RPC and no session to carry a standing threshold. When the
 * request does set a level, only messages at or above it may be sent.
 */
class LogLevelGatingTest extends AbstractStatelessMcpE2eTest {

    // language=JSON
    private static final String NO_ARGS_SCHEMA = "{\"type\": \"object\", \"properties\": {}}";

    @BeforeEach
    void registerFixtures() {
        startServer(b -> b.capabilities(c -> c.logging())
                .tool(
                        new AbstractToolHandler(ToolDescriptor.builder()
                                .name("test_logging_tool")
                                .description("Emits an INFO log message then completes")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .build()) {
                            @Override
                            public ToolResult handle(InteractionContext ctx, ToolRequest request) {
                                ctx.notifications().log(LoggingLevel.INFO, "test", "hello");
                                return ToolResult.text("done");
                            }
                        }));
    }

    @Test
    void suppressesLogWithoutRequestLogLevel() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.sendStreamingRequest(null, """
                    {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "test_logging_tool", "arguments": {}}}
                    """);
            var lines = response.body().toList();

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(lines).noneMatch(line -> line.contains("notifications/message"));
            assertThat(lines).anyMatch(line -> line.contains("\"result\""));
        }
    }

    @Test
    void emitsLogWhenRequestLogLevelPermits() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.sendStreamingRequest(null, """
                    {"jsonrpc": "2.0", "id": 2, "method": "tools/call",
                     "params": {"name": "test_logging_tool", "arguments": {},
                                "_meta": {"io.modelcontextprotocol/logLevel": "info"}}}
                    """);
            var lines = response.body().toList();

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(lines)
                    .anyMatch(line -> line.contains("notifications/message") && line.contains("\"level\":\"info\""));
        }
    }
}
