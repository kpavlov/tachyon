/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.Args;
import dev.tachyonmcp.server.domain.FormInputRequest;
import dev.tachyonmcp.server.domain.InputRequest;
import dev.tachyonmcp.server.domain.UrlInputRequest;
import dev.tachyonmcp.server.features.tools.AbstractToolHandler;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class InputRequiredResultTest extends AbstractStatelessMcpE2eTest {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    @Test
    void basicElicitationFlow() throws Exception {
        startServer(it -> it.tool(new InputRequiredTestHandler()));

        try (var client = createTestClient()) {
            client.initialize();

            // Round 1: call tool without inputResponses -> expect InputRequiredResult
            var round1 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"test_elicitation","arguments":{}}}
                """);

            assertThatJson(round1.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.user_name.method")
                    .isEqualTo("elicitation/create");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.user_name.params.message")
                    .isEqualTo("What is your name?");

            // Round 2: call tool with inputResponses -> expect complete result
            var round2 = client.post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"test_elicitation","arguments":{},"inputResponses":{"user_name":{"name":"Alice"}}}}
                """);

            assertThatJson(round2.body()).inPath("$.result.content[0].text").isEqualTo("Hello, Alice!");
        }
    }

    @Test
    void multiRoundFlow() throws Exception {
        startServer(it -> it.tool(new MultiRoundTestHandler()));

        try (var client = createTestClient()) {
            client.initialize();

            // Round 1: call tool without inputResponses -> expect InputRequiredResult with step1
            var round1 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"test_multi_round","arguments":{}}}
                """);

            assertThatJson(round1.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.step1.params.message")
                    .isEqualTo("Step 1: What is your name?");
            var state1 = extractRequestState(round1.body());
            assertThat(state1).isEqualTo("state-round-1");

            // Round 2: call tool with step1 inputResponses + requestState -> expect another InputRequiredResult
            var round2 = client.post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"test_multi_round","arguments":{},"inputResponses":{"step1":{"name":"Bob"}},"requestState":"state-round-1"}}
                """);

            assertThatJson(round2.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(round2.body())
                    .inPath("$.result.inputRequests.step2.params.message")
                    .isEqualTo("Step 2: What is your favorite color?");
            var state2 = extractRequestState(round2.body());
            assertThat(state2).startsWith("state-round-2:");

            // Round 3: call tool with step2 inputResponses + updated requestState -> expect complete result
            var round3 = client.post(
                    "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"test_multi_round\",\"arguments\":{},\"inputResponses\":{\"step2\":{\"color\":\"blue\"}},\"requestState\":\""
                            + state2 + "\"}}");

            assertThatJson(round3.body())
                    .inPath("$.result.content[0].text")
                    .isEqualTo("Hello, Bob! Your favorite color is blue.");
        }
    }

    @Test
    void inputRequiredPreservesMeta() throws Exception {
        startServer(it -> it.tool(new MetaInputRequiredTestHandler()));

        try (var client = createTestClient()) {
            client.initialize();

            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"test_meta_elicitation","arguments":{}}}
                """);

            assertThatJson(response.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(response.body()).inPath("$.result._meta.trace-id").isEqualTo("abc-123");
        }
    }

    @Test
    void missingInputResponsesReRequests() throws Exception {
        startServer(it -> it.tool(new InputRequiredTestHandler()));

        try (var client = createTestClient()) {
            client.initialize();

            // Call tool with wrong keys in inputResponses -> expect new InputRequiredResult
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"test_elicitation","arguments":{},"inputResponses":{"wrong_key":{"name":"Alice"}}}}
                """);

            assertThatJson(response.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(response.body())
                    .inPath("$.result.inputRequests.user_name.method")
                    .isEqualTo("elicitation/create");
            assertThatJson(response.body())
                    .inPath("$.result.inputRequests.user_name.params.message")
                    .isEqualTo("What is your name?");
        }
    }

    @Test
    void urlModeElicitationFlow() throws Exception {
        startServer(it -> it.tool(new UrlElicitationTestHandler()));

        try (var client = createTestClient()) {
            client.initialize();

            // Round 1: tool requires URL-mode authentication
            var round1 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"test_url_elicitation","arguments":{}}}
                """);

            assertThatJson(round1.body()).inPath("$.result.resultType").isEqualTo("input_required");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.auth.method")
                    .isEqualTo("elicitation/create");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.auth.params.mode")
                    .isEqualTo("url");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.auth.params.message")
                    .isEqualTo("Please authenticate via the provided URL.");
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.auth.params.url")
                    .isString();
            assertThatJson(round1.body())
                    .inPath("$.result.inputRequests.auth.params.elicitationId")
                    .isEqualTo("auth-elicitation-1");

            // Round 2: client signals URL auth complete with action=accept
            var round2 = client.post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"test_url_elicitation","arguments":{},"inputResponses":{"auth":{"action":"accept"}}}}
                """);

            assertThatJson(round2.body()).inPath("$.result.content[0].text").isEqualTo("Authenticated successfully!");
        }
    }

    private static @Nullable String extractRequestState(String responseBody) {
        try {
            var mapper = new tools.jackson.databind.ObjectMapper();
            var tree = mapper.readTree(responseBody);
            var state = tree.at("/result/requestState");
            return state.isString() ? state.asString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static class InputRequiredTestHandler extends AbstractToolHandler {

        InputRequiredTestHandler() {
            super(ToolDescriptor.builder()
                    .name("test_elicitation")
                    .description("Tests InputRequiredResult flow")
                    .build());
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolRequest request) {
            var inputResponses = request.inputResponses();
            if (inputResponses != null && inputResponses.containsKey("user_name")) {
                var resp = inputResponses.get("user_name");
                var name = resp != null && resp.has("name") ? resp.get("name").asString() : "World";
                return ToolResult.text("Hello, " + name + "!");
            }
            var inputRequests = Map.of("user_name", buildFormElicitation("What is your name?", "name", "string"));
            return ToolResult.inputRequired(inputRequests, null);
        }
    }

    private static FormInputRequest buildFormElicitation(String message, String propName, String propType) {
        var schemaMap = new LinkedHashMap<String, JsonNode>();
        schemaMap.put("type", FACTORY.stringNode("object"));
        var propsNode = FACTORY.objectNode();
        propsNode.putObject(propName).put("type", propType);
        schemaMap.put("properties", propsNode);
        schemaMap.put("required", FACTORY.arrayNode().add(propName));
        return FormInputRequest.of(message, schemaMap);
    }

    private static UrlInputRequest buildUrlElicitation(String message, String elicitationId, String url) {
        return UrlInputRequest.of(message, elicitationId, url);
    }

    private static class MultiRoundTestHandler extends AbstractToolHandler {

        MultiRoundTestHandler() {
            super(ToolDescriptor.builder()
                    .name("test_multi_round")
                    .description("Tests multi-round InputRequiredResult flow")
                    .build());
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolRequest request) {
            var inputResponses = request.inputResponses();
            var requestState = request.requestState();

            if (requestState != null && requestState.startsWith("state-round-2:")) {
                var name = requestState.substring("state-round-2:".length());
                var color = inputResponses != null && inputResponses.containsKey("step2")
                        ? inputResponses.get("step2").path("color").asString("unknown")
                        : "unknown";
                return ToolResult.text("Hello, " + name + "! Your favorite color is " + color + ".");
            }

            if ("state-round-1".equals(requestState) && inputResponses != null && inputResponses.containsKey("step1")) {
                var name = inputResponses.get("step1").path("name").asString("unknown");
                var inputRequests = new LinkedHashMap<String, InputRequest>();
                inputRequests.put(
                        "step2", buildFormElicitation("Step 2: What is your favorite color?", "color", "string"));
                return ToolResult.inputRequired(inputRequests, "state-round-2:" + name);
            }

            var inputRequests = new LinkedHashMap<String, InputRequest>();
            inputRequests.put("step1", buildFormElicitation("Step 1: What is your name?", "name", "string"));
            return ToolResult.inputRequired(inputRequests, "state-round-1");
        }
    }

    private static class MetaInputRequiredTestHandler extends AbstractToolHandler {

        MetaInputRequiredTestHandler() {
            super(ToolDescriptor.builder()
                    .name("test_meta_elicitation")
                    .description("Tests InputRequiredResult meta propagation")
                    .build());
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolRequest request) {
            var inputRequests = Map.of("user_name", buildFormElicitation("What is your name?", "name", "string"));
            return ToolResult.inputRequired(inputRequests, null).withMeta("trace-id", FACTORY.stringNode("abc-123"));
        }
    }

    private static class UrlElicitationTestHandler extends AbstractToolHandler {

        UrlElicitationTestHandler() {
            super(ToolDescriptor.builder()
                    .name("test_url_elicitation")
                    .description("Tests URL-mode elicitation flow")
                    .build());
        }

        @Override
        public CompletionStage<? extends ToolResult> handleAsync(InteractionContext context, Args args) {
            return handleAsync(
                    context, ToolRequest.builder().name(descriptor().name()).build());
        }

        @Override
        public CompletionStage<ToolResult> handleAsync(InteractionContext context, ToolRequest request) {
            var inputResponses = request.inputResponses();
            if (inputResponses != null && inputResponses.containsKey("auth")) {
                var resp = inputResponses.get("auth");
                var action =
                        resp != null && resp.has("action") ? resp.get("action").asString() : "";
                if ("accept".equals(action)) {
                    return CompletableFuture.completedFuture(ToolResult.text("Authenticated successfully!"));
                }
                return CompletableFuture.completedFuture(ToolResult.error("Authentication declined or cancelled."));
            }
            var inputRequests = Map.of(
                    "auth",
                    buildUrlElicitation(
                            "Please authenticate via the provided URL.",
                            "auth-elicitation-1",
                            "https://example.com/auth"));
            return CompletableFuture.supplyAsync(() -> {
                Awaitility.await().timeout(Duration.ofMillis(100));
                return ToolResult.inputRequired(inputRequests, null);
            });
        }
    }
}
