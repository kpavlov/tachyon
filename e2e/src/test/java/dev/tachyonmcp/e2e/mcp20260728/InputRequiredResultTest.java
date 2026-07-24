/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.FormInputRequest;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.RpcMethodRequest;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 Multi Round-Trip Requests (SEP-2322): a server can respond to {@code tools/call}
 * with an {@code InputRequiredResult} carrying elicitation/sampling/roots-list {@code inputRequests}
 * and/or an opaque {@code requestState}, driven fully statelessly (no session). The underlying
 * {@code ToolResult.inputRequired(...)} machinery already worked before this change — these
 * scenarios only ever failed because the session gate rejected the request before it reached the
 * tool (see {@code StatelessDispatchTest}).
 */
class InputRequiredResultTest extends AbstractStatelessMcpE2eTest {

    // language=JSON
    private static final String NO_ARGS_SCHEMA = "{\"type\": \"object\", \"properties\": {}}";

    private static FormInputRequest elicitation(String message, String prop) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Map.of(prop, Map.of("type", "string")));
        return FormInputRequest.of(message, schema);
    }

    private static RpcMethodRequest samplingRequest() {
        var message =
                Map.of("role", "user", "content", Map.of("type", "text", "text", "What is the capital of France?"));
        return RpcMethodRequest.of("sampling/createMessage", Map.of("messages", List.of(message), "maxTokens", 100));
    }

    private static RpcMethodRequest rootsListRequest() {
        return RpcMethodRequest.of("roots/list", Map.of());
    }

    @BeforeEach
    void registerFixtures() {
        startServer(b -> b.tool(
                        new AbstractToolHandler(ToolDescriptor.builder()
                                .name("elicit_name")
                                .description("Elicits a name")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .build()) {
                            @Override
                            public ToolResult handle(InteractionContext ctx, ToolRequest request) {
                                var responses = request.inputResponses();
                                if (responses != null && responses.containsKey("user_name")) {
                                    var name =
                                            stringField(field(responses.get("user_name"), "content"), "name", "World");
                                    return ToolResult.text("Hello, " + name + "!");
                                }
                                return ToolResult.inputRequired(
                                        Map.of("user_name", elicitation("What is your name?", "name")), null);
                            }
                        })
                .tool(
                        new AbstractToolHandler(ToolDescriptor.builder()
                                .name("ask_sampling")
                                .description("Requests sampling")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .build()) {
                            @Override
                            public ToolResult handle(InteractionContext ctx, ToolRequest request) {
                                var responses = request.inputResponses();
                                if (responses != null && responses.containsKey("capital_question")) {
                                    return ToolResult.text("done");
                                }
                                return ToolResult.inputRequired(Map.of("capital_question", samplingRequest()), null);
                            }
                        })
                .tool(
                        new AbstractToolHandler(ToolDescriptor.builder()
                                .name("ask_roots")
                                .description("Requests roots/list")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .build()) {
                            @Override
                            public ToolResult handle(InteractionContext ctx, ToolRequest request) {
                                var responses = request.inputResponses();
                                if (responses != null && responses.containsKey("client_roots")) {
                                    return ToolResult.text("roots received");
                                }
                                return ToolResult.inputRequired(Map.of("client_roots", rootsListRequest()), null);
                            }
                        })
                .tool(
                        new AbstractToolHandler(ToolDescriptor.builder()
                                .name("respect_capabilities")
                                .description("Only asks for declared capabilities")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .build()) {
                            @Override
                            public ToolResult handle(InteractionContext ctx, ToolRequest request) {
                                var meta = request.meta();
                                var capabilities =
                                        meta != null ? meta.get("io.modelcontextprotocol/clientCapabilities") : null;
                                var hasSampling = field(capabilities, "sampling") != null;
                                if (!hasSampling) {
                                    return ToolResult.text("No sampling capability declared");
                                }
                                return ToolResult.inputRequired(Map.of("capital_question", samplingRequest()), null);
                            }
                        })
                .tool(
                        new AbstractToolHandler(ToolDescriptor.builder()
                                .name("ask_multiple")
                                .description("Requests multiple inputs at once")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .build()) {
                            @Override
                            public ToolResult handle(InteractionContext ctx, ToolRequest request) {
                                var responses = request.inputResponses();
                                if (responses != null
                                        && responses.containsKey("user_name")
                                        && responses.containsKey("client_roots")) {
                                    return ToolResult.text("all inputs received");
                                }
                                var inputRequests = new LinkedHashMap<String, InputRequest>();
                                inputRequests.put("user_name", elicitation("What is your name?", "name"));
                                inputRequests.put("client_roots", rootsListRequest());
                                return ToolResult.inputRequired(inputRequests, "multi-input-state");
                            }
                        }));
    }

    private static Object field(Object value, String name) {
        return value instanceof Map<?, ?> map ? map.get(name) : null;
    }

    private static String stringField(Object value, String name, String defaultValue) {
        var field = field(value, name);
        return field instanceof String text ? text : defaultValue;
    }

    private String toolCallBody(int id, String toolName, String inputResponsesJson) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": %d,
                  "method": "tools/call",
                  "params": {"name": "%s", "arguments": {}%s}
                }
                """.formatted(id, toolName, inputResponsesJson.isEmpty() ? "" : "," + inputResponsesJson);
    }

    @Test
    void basicElicitationFlow() throws Exception {
        try (var client = createModernTestClient()) {
            var round1 = client.post(toolCallBody(1, "elicit_name", ""));
            assertThat(round1.statusCode()).as(round1.body()).isEqualTo(200);
            assertThatJson(round1.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.user_name.method")
                    .isEqualTo("elicitation/create");

            var round2 = client.post(
                    toolCallBody(
                            2,
                            "elicit_name",
                            "\"inputResponses\": {\"user_name\": {\"action\": \"accept\", \"content\": {\"name\": \"Alice\"}}}"));
            assertThat(round2.statusCode()).as(round2.body()).isEqualTo(200);
            assertThatJson(round2.body()).inPath("$.result.content[0].text").isEqualTo("Hello, Alice!");
        }
    }

    @Test
    void basicSamplingFlow() throws Exception {
        try (var client = createModernTestClient()) {
            var round1 = client.post(toolCallBody(3, "ask_sampling", ""));
            assertThat(round1.statusCode()).as(round1.body()).isEqualTo(200);
            assertThatJson(round1.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.capital_question.method")
                    .isEqualTo("sampling/createMessage");
        }
    }

    @Test
    void basicListRootsFlow() throws Exception {
        try (var client = createModernTestClient()) {
            var round1 = client.post(toolCallBody(4, "ask_roots", ""));
            assertThat(round1.statusCode()).as(round1.body()).isEqualTo(200);
            assertThatJson(round1.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.client_roots.method")
                    .isEqualTo("roots/list");
        }
    }

    @Test
    void multipleInputRequestsFlow() throws Exception {
        try (var client = createModernTestClient()) {
            var round1 = client.post(toolCallBody(5, "ask_multiple", ""));
            assertThat(round1.statusCode()).as(round1.body()).isEqualTo(200);
            assertThatJson(round1.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.user_name.method")
                    .isEqualTo("elicitation/create");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.client_roots.method")
                    .isEqualTo("roots/list");
            assertThatJson(round1.body()).inPath("$.result.requestState").isEqualTo("multi-input-state");
        }
    }

    @Test
    void respectsDeclaredCapabilities() throws Exception {
        try (var client = createModernTestClient()) {
            // Mcp20260728TestClient always declares an empty clientCapabilities object — no
            // "sampling" key — so the tool must not include a sampling inputRequest.
            var response = client.post(toolCallBody(6, "respect_capabilities", ""));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .inPath("$.result.content[0].text")
                    .isEqualTo("No sampling capability declared");
        }
    }

    @Test
    void includesInputRequestWhenCapabilityIsDeclared() throws Exception {
        // language=JSON
        var body = """
                {
                  "jsonrpc": "2.0",
                  "id": 7,
                  "method": "tools/call",
                  "params": {
                    "name": "respect_capabilities",
                    "arguments": {},
                    "_meta": {
                      "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                      "io.modelcontextprotocol/clientInfo": {"name": "t", "version": "1"},
                      "io.modelcontextprotocol/clientCapabilities": {"sampling": {}}
                    }
                  }
                }
                """;
        var response = postMcpRequest(body, Map.of("Mcp-Method", "tools/call", "Mcp-Name", "respect_capabilities"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThatJson(response.body()).inPath("$.result.resultType").isEqualTo("input_required");
        assertThatJson(response.body())
                .inPath("$.result.inputRequests.capital_question.method")
                .isEqualTo("sampling/createMessage");
    }
}
