/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.domain.LoggingLevel;
import dev.tachyonmcp.server.domain.MissingRequiredClientCapabilityException;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptResult;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.internal.ServerEngine;
import java.util.List;
import java.util.Map;

class EdgeConformanceServer extends AbstractConformanceServer {

    @Override
    protected ServerEngine createServer(boolean isStateful) {
        return (ServerEngine) TachyonServer.builder()
                .session(s -> s.enabled(isStateful))
                .toolRequest(
                        ToolDescriptor.builder()
                                .name("test_missing_capability")
                                .description("SEP-2575 requires an explicitly declared capability")
                                .inputSchema(INPUT_SCHEMA_NO_ARGS)
                                .build(),
                        (ctx, request) -> {
                            var meta = request.meta();
                            var capabilities =
                                    meta != null ? meta.get("io.modelcontextprotocol/clientCapabilities") : null;
                            var hasSampling = capabilities != null
                                    && !capabilities.path("sampling").isMissingNode();
                            if (!hasSampling) {
                                throw new MissingRequiredClientCapabilityException(
                                        "Requires the 'sampling' capability", Map.of("sampling", Map.of()));
                            }
                            return ToolResult.text("sampling capability present");
                        })
                .toolRequest(
                        ToolDescriptor.builder()
                                .name("test_logging_tool")
                                .description(
                                        "SEP-2575 emits a log message; must be suppressed without _meta.../logLevel")
                                .inputSchema(INPUT_SCHEMA_NO_ARGS)
                                .build(),
                        (ctx, request) -> {
                            ctx.notifications().log(LoggingLevel.INFO, "test", "diagnostic log message");
                            return ToolResult.text("logged");
                        })
                .toolRequest(
                        ToolDescriptor.builder()
                                .name("test_streaming_elicitation")
                                .description(
                                        "SEP-2575 response stream carries only notifications, never independent requests")
                                .inputSchema(INPUT_SCHEMA_NO_ARGS)
                                .build(),
                        (ctx, request) -> {
                            ctx.notifications().progress(request.progressToken(), 1, 1, "working");
                            return ToolResult.text("streamed");
                        })
                .toolRequest(
                        ToolDescriptor.builder()
                                .name("test_custom_header")
                                .description("SEP-2243 x-mcp-header/Mcp-Param-* custom header validation")
                                .inputSchema(parseJson("""
                        {
                          "type": "object",
                          "properties": {
                            "region": {"type": "string", "x-mcp-header": "Region"},
                            "query": {"type": "string"}
                          },
                          "required": ["region", "query"]
                        }
                        """))
                                .build(),
                        (ctx, request) -> {
                            var args = request.arguments();
                            var region = args.get("region");
                            var query = args.get("query");
                            return ToolResult.text("region=" + (region != null ? region.asString("") : "") + " query="
                                    + (query != null ? query.asString("") : ""));
                        })
                .build();
    }

    /**
     * SEP-2322 InputRequiredResult tools, draft-only (protocol version 2026-07-28): the
     * {@code input-required-result-*} conformance scenarios don't apply to the stable suite.
     */
    @Override
    protected void registerVersionSpecificTools(ServerEngine server) {
        registerInputRequiredTools(server);
    }

    @Override
    protected void registerVersionSpecificPrompts(ServerEngine server) {
        server.prompts()
                .register(
                        PromptDescriptor.of(
                                "test_input_required_result_prompt", "Prompt requiring elicitation input (SEP-2322)"),
                        (ctx, request) -> {
                            var inputResponses = request.inputResponses();
                            if (inputResponses != null && inputResponses.containsKey("user_context")) {
                                return PromptResult.messages(List.of(PromptMessage.user("Context received")));
                            }
                            return PromptResult.inputRequired(
                                    Map.of(
                                            "user_context",
                                            buildFormElicitation(
                                                    "What context should the prompt use?", "context", "string")),
                                    null);
                        });
    }
}
