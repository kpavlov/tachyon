/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.server.domain.FormInputRequest;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.features.completions.CompletionResult;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.prompts.PromptResult;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.api.server.features.resources.ResourceTemplateDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * E2E for descriptor/handler metadata propagation: {@code _meta} on descriptors
 * flows through list operations, and request {@code _meta} reaches handlers
 * and appears in results for tools, prompts, and completions.
 */
class MetadataE2eTest extends AbstractStatelessMcpE2eTest {

    // language=JSON
    private static final String NO_ARGS_SCHEMA = "{\"type\": \"object\", \"properties\": {}}";

    private static FormInputRequest elicitation(String message, String prop) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Map.of(prop, Map.of("type", "string")));
        return FormInputRequest.of(message, schema);
    }

    private static Object field(Object value, String name) {
        return value instanceof Map<?, ?> map ? map.get(name) : null;
    }

    @BeforeEach
    void registerFixtures() {
        startServerWith(s -> {
            s.tools()
                    .register(
                            tool -> tool.name("structured_array")
                                    .description("Returns an arbitrary structured JSON value")
                                    .inputSchema(NO_ARGS_SCHEMA)
                                    .meta(Map.of("catalog", "tool")),
                            (ctx, request) -> {
                                var meta = request.meta();
                                var trace = meta != null ? meta.get("com.example/trace") : null;
                                var result = ToolResult.structured(JsonDocument.of("[1,true]"));
                                return trace != null ? result.withMeta("echo-trace", trace) : result;
                            });
            s.resources()
                    .register(
                            ResourceDescriptor.builder()
                                    .name("interactive-resource")
                                    .uri("memory://interactive")
                                    .meta(Map.of("catalog", "resource"))
                                    .build(),
                            (ctx, request) -> TextResourceContents.of(
                                    request.uri(),
                                    field(request.inputResponses(), "answer") + ":" + request.requestState(),
                                    "text/plain",
                                    Map.of("source", "interactive-resource")))
                    .registerTemplate(
                            ResourceTemplateDescriptor.builder()
                                    .name("interactive-template")
                                    .uriTemplate("memory://interactive/{id}")
                                    .meta(Map.of("catalog", "template"))
                                    .build(),
                            (ctx, request) -> TextResourceContents.of(request.uri(), "template", "text/plain"));
            s.prompts()
                    .register(
                            PromptDescriptor.builder()
                                    .name("interactive-prompt")
                                    .description("Interactive prompt")
                                    .meta(Map.of("catalog", "prompt"))
                                    .build(),
                            (ctx, request) -> {
                                var result = PromptResult.messages(List.of(PromptMessage.user(
                                        field(request.inputResponses(), "answer") + ":" + request.requestState())));
                                return request.meta() != null && request.meta().containsKey("com.example/trace")
                                        ? result.withMeta(
                                                "echo-trace", request.meta().get("com.example/trace"))
                                        : result;
                            })
                    .register(
                            PromptDescriptor.builder().name("input-meta-prompt").build(),
                            (ctx, request) -> PromptResult.inputRequired(
                                            Map.of("answer", elicitation("Answer?", "value")), "prompt-input-round")
                                    .withMeta("trace", "input-required"));
            s.completions().registerForPrompt("meta-completion", (ctx, request) -> {
                var meta = request.meta();
                var trace = meta != null ? meta.get("com.example/trace") : null;
                var result = CompletionResult.builder().values(request.argumentValue() + "-complete");
                return trace != null ? result.meta(Map.of("echo-trace", trace)).build() : result.build();
            });
        });
    }

    @Test
    void toolCallPreservesArbitraryRequestMetaAndStructuredJsonValue() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {
                      "jsonrpc": "2.0",
                      "id": 10,
                      "method": "tools/call",
                      "params": {
                        "name": "structured_array",
                        "arguments": {},
                        "_meta": {"com.example/trace": "trace-7"}
                      }
                    }
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 10,
                      "result": {
                        "content": [{"type": "text", "text": "[1,true]"}],
                        "structuredContent": [1, true],
                        "_meta": {"echo-trace": "trace-7"},
                        "resultType": "complete"
                      }
                    }
                    """);
        }
    }

    @Test
    void toolAndCompletionAcceptMissingMetadata() throws Exception {
        try (var client = createModernTestClient()) {
            var tool = client.post("""
                    {
                      "jsonrpc": "2.0",
                      "id": 11,
                      "method": "tools/call",
                      "params": {"name": "structured_array", "arguments": {}}
                    }
                    """);
            var completion = client.post("""
                    {
                      "jsonrpc": "2.0",
                      "id": 12,
                      "method": "completion/complete",
                      "params": {
                        "ref": {"type": "ref/prompt", "name": "meta-completion"},
                        "argument": {"name": "name", "value": "B"}
                      }
                    }
                    """);

            assertThat(tool.statusCode()).as(tool.body()).isEqualTo(200);
            assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
            assertThatJson(tool.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 11,
                      "result": {
                        "content": [{"type": "text", "text": "[1,true]"}],
                        "structuredContent": [1, true],
                        "resultType": "complete"
                      }
                    }
                    """);
            assertThatJson(completion.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 12,
                      "result": {
                        "completion": {"values": ["B-complete"]},
                        "resultType": "complete"
                      }
                    }
                    """);
        }
    }

    @Test
    void descriptorMetadataFlowsThroughAllListOperations() throws Exception {
        try (var client = createModernTestClient()) {
            var tools = client.post("{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/list\",\"params\":{}}");
            var resources = client.post("{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"resources/list\",\"params\":{}}");
            var templates = client.post(
                    "{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"resources/templates/list\",\"params\":{}}");
            var prompts = client.post("{\"jsonrpc\":\"2.0\",\"id\":14,\"method\":\"prompts/list\",\"params\":{}}");

            assertThat(tools.statusCode()).as(tools.body()).isEqualTo(200);
            assertThat(resources.statusCode()).as(resources.body()).isEqualTo(200);
            assertThat(templates.statusCode()).as(templates.body()).isEqualTo(200);
            assertThat(prompts.statusCode()).as(prompts.body()).isEqualTo(200);
            assertThatJson(tools.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 11,
                      "result": {
                        "tools": [
                          {
                            "description": "Returns an arbitrary structured JSON value",
                            "inputSchema": {"type": "object", "properties": {}},
                            "_meta": {"catalog": "tool"},
                            "name": "structured_array"
                          }
                        ],
                        "resultType": "complete",
                        "ttlMs": 0,
                        "cacheScope": "public"
                      }
                    }
                    """);
            assertThatJson(resources.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 12,
                      "result": {
                        "resources": [{
                          "uri": "memory://interactive",
                          "_meta": {"catalog": "resource"},
                          "name": "interactive-resource"
                        }],
                        "resultType": "complete",
                        "ttlMs": 0,
                        "cacheScope": "public"
                      }
                    }
                    """);
            assertThatJson(templates.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 13,
                      "result": {
                        "resourceTemplates": [{
                          "uriTemplate": "memory://interactive/{id}",
                          "_meta": {"catalog": "template"},
                          "name": "interactive-template"
                        }],
                        "resultType": "complete",
                        "ttlMs": 0,
                        "cacheScope": "public"
                      }
                    }
                    """);
            assertThatJson(prompts.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 14,
                      "result": {
                        "prompts": [
                          {"name": "input-meta-prompt"},
                          {
                            "description": "Interactive prompt",
                            "_meta": {"catalog": "prompt"},
                            "name": "interactive-prompt"
                          }
                        ],
                        "resultType": "complete",
                        "ttlMs": 0,
                        "cacheScope": "public"
                      }
                    }
                    """);
        }
    }

    @Test
    void resourceRetryCarriesInputResponsesAndRequestState() throws Exception {
        // language=JSON
        var body = """
                {
                  "jsonrpc": "2.0",
                  "id": 8,
                  "method": "resources/read",
                  "params": {
                    "uri": "memory://interactive",
                    "inputResponses": {"answer": "Paris"},
                    "requestState": "resource-round-1"
                  }
                }
                """;

        try (var client = createModernTestClient()) {
            var response = client.post(body);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body()).isEqualTo("""
                            {
                              "jsonrpc": "2.0",
                              "id": 8,
                              "result": {
                                "contents": [{
                                  "uri": "memory://interactive",
                                  "mimeType": "text/plain",
                                  "text": "Paris:resource-round-1",
                                  "_meta": {"source": "interactive-resource"}
                                }],
                                "resultType": "complete",
                                "ttlMs": 0,
                                "cacheScope": "public"
                              }
                            }
                            """);
        }
    }

    @Test
    void promptRetryCarriesInputResponsesAndRequestState() throws Exception {
        // language=JSON
        var body = """
                {
                  "jsonrpc": "2.0",
                  "id": 9,
                  "method": "prompts/get",
                  "params": {
                    "name": "interactive-prompt",
                    "inputResponses": {"answer": "Paris"},
                    "requestState": "prompt-round-1"
                  }
                }
                """;

        try (var client = createModernTestClient()) {
            var response = client.post(body);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body()).isEqualTo("""
                            {
                              "jsonrpc": "2.0",
                              "id": 9,
                              "result": {
                                "description": "Interactive prompt",
                                "messages": [{
                                  "role": "user",
                                  "content": {"type": "text", "text": "Paris:prompt-round-1"}
                                }],
                                "resultType": "complete"
                              }
                            }
                            """);
        }
    }

    @Test
    void promptAndCompletionMetadataReachHandlersAndResults() throws Exception {
        try (var client = createModernTestClient()) {
            var prompt = client.post("""
                    {
                      "jsonrpc": "2.0",
                      "id": 15,
                      "method": "prompts/get",
                      "params": {
                        "name": "interactive-prompt",
                        "_meta": {"com.example/trace": "prompt-trace"}
                      }
                    }
                    """);
            var completion = client.post("""
                    {
                      "jsonrpc": "2.0",
                      "id": 16,
                      "method": "completion/complete",
                      "params": {
                        "ref": {"type": "ref/prompt", "name": "meta-completion"},
                        "argument": {"name": "name", "value": "A"},
                        "_meta": {"com.example/trace": "completion-trace"}
                      }
                    }
                    """);
            var inputRequired = client.post("""
                    {
                      "jsonrpc": "2.0",
                      "id": 17,
                      "method": "prompts/get",
                      "params": {"name": "input-meta-prompt"}
                    }
                    """);

            assertThat(prompt.statusCode()).as(prompt.body()).isEqualTo(200);
            assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
            assertThat(inputRequired.statusCode()).as(inputRequired.body()).isEqualTo(200);
            assertThatJson(prompt.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 15,
                      "result": {
                        "description": "Interactive prompt",
                        "messages": [{
                          "role": "user",
                          "content": {"type": "text", "text": "null:null"}
                        }],
                        "_meta": {"echo-trace": "prompt-trace"},
                        "resultType": "complete"
                      }
                    }
                    """);
            assertThatJson(completion.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 16,
                      "result": {
                        "completion": {"values": ["A-complete"]},
                        "_meta": {"echo-trace": "completion-trace"},
                        "resultType": "complete"
                      }
                    }
                    """);
            assertThatJson(inputRequired.body()).isEqualTo("""
                    {
                      "jsonrpc": "2.0",
                      "id": 17,
                      "result": {
                        "resultType": "input_required",
                        "inputRequests": {
                          "answer": {
                            "method": "elicitation/create",
                            "params": {
                              "message": "Answer?",
                              "requestedSchema": {
                                "type": "object",
                                "properties": {"value": {"type": "string"}}
                              }
                            }
                          }
                        },
                        "requestState": "prompt-input-round",
                        "_meta": {"trace": "input-required"}
                      }
                    }
                    """);
        }
    }
}
