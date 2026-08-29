/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.json.JsonSchemaValidator;
import dev.tachyonmcp.api.server.domain.PromptArgument;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.Role;
import dev.tachyonmcp.api.server.domain.TextContent;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.json.NetworkntJsonSchemaValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class SchemaValidationTest extends AbstractStatelessMcpE2eTest {

    private static final JsonSchemaValidator VALIDATOR = new NetworkntJsonSchemaValidator();

    private static final JsonSchema TOOL_SCHEMA = JsonSchema.from(buildToolSchema(), JsonNode.class);
    private static final JsonSchema PROMPT_SCHEMA = JsonSchema.from(buildPromptSchema(), JsonNode.class);

    // region: Tool input schema validation

    @Test
    void shouldValidateMultipleToolsWithDistinctSchemas() throws Exception {
        startServer(
                b -> b.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR)),
                s -> s.tools()
                        .register(validatedTool(), (context, request) -> ToolResult.text("ok"))
                        .register(validatedTool2(), (context, request) -> ToolResult.text("ok")));

        try (var client = createTestClient()) {
            client.initialize();

            var r1 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"validated","arguments":{"name":"John","age":30}}}
                """);
            assertThat(r1.statusCode()).isEqualTo(200);
            assertThatJson(r1.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"ok"}]}}
                """);

            var r2 = client.post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"validated2","arguments":{"email":"john@example.com","age":25}}}
                """);
            assertThat(r2.statusCode()).isEqualTo(200);
            assertThatJson(r2.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"ok"}]}}
                """);

            var r3 = client.post("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"validated2","arguments":{"email":"john@example.com"}}}
                """);
            assertThat(r3.statusCode()).isEqualTo(200);
            assertThatJson(r3.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":4,"result":{"content":[{"type":"text","text":"ok"}]}}
                """);
        }
    }

    @Test
    void shouldAcceptValidToolArguments() throws Exception {
        startServer(
                b -> b.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR)),
                s -> s.tools().register(validatedTool(), (context, request) -> ToolResult.text("ok")));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"validated","arguments":{"name":"John","age":30}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"ok"}]}}
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void shouldRejectToolCallWithMissingRequiredField() throws Exception {
        startServer(
                b -> b.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR)),
                s -> s.tools().register(validatedTool(), (context, request) -> ToolResult.text("ok")));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"validated","arguments":{"age":30}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"required property 'name' not found"}}
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void shouldRejectToolCallWithWrongType() throws Exception {
        startServer(
                b -> b.json(j -> j.schemaValidator(VALIDATOR)),
                s -> s.tools().register(validatedTool(), (context, request) -> ToolResult.text("ok")));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"validated","arguments":{"name":123}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);

            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"integer found, string expected"}}
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    // endregion

    // region: Tool output schema validation

    @Test
    void shouldRejectToolCallWithInvalidStructuredOutput() throws Exception {
        // language=json
        var outputSchema = """
            {"type":"object","properties":{"count":{"type":"integer"}},"required":["count"]}
            """;
        startServer(
                b -> b.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR)),
                s -> s.tools()
                        .register(
                                ToolDescriptor.builder()
                                        .name("bad-structured-output")
                                        .description("Returns structured output that violates its own outputSchema")
                                        .outputSchema(outputSchema)
                                        .build(),
                                // "count" is a string, not the integer the outputSchema requires.
                                (context, request) -> ToolResult.structured(Map.of("count", "not-a-number"))));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"bad-structured-output","arguments":{}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response).isSuccess().hasId(2).isToolError().hasTextContent("string found, integer expected");
        }
    }

    @Test
    void shouldFallBackToTextOnlyForArrayStructuredContentUnder20251125() throws Exception {
        // 2026-07-28 permits array/scalar outputSchema and structuredContent (see
        // dev.tachyonmcp.e2e.mcp20260728.StructuredOutputSchemaShapeTest); 2025-11-25's
        // structuredContent is object-only on the wire, so a valid array result falls back to the
        // backwards-compat text block instead of being rejected or crashing.
        startServer(
                b -> b.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR)),
                s -> s.tools()
                        .register(
                                ToolDescriptor.builder()
                                        .name("array-structured-output")
                                        .description("Returns an array structured result")
                                        .outputSchema("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}")
                                        .build(),
                                (context, request) -> ToolResult.structured(List.of(1, 2, 3))));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"array-structured-output","arguments":{}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response).isSuccess().hasId(2).hasTextContent("[1,2,3]");
        }
    }

    @Test
    void shouldEmitBothHandlerTextAndArrayFallbackTextUnder20251125() throws Exception {
        // Regression: for a non-object structured value, the handler's own text block (from
        // ToolResult.structured(payload, text)) must not suppress the serialized-JSON fallback --
        // under 2025-11-25 that fallback is the array's only representation on the wire, since
        // structuredContent can't carry it.
        startServer(
                b -> b.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR)),
                s -> s.tools()
                        .register(
                                ToolDescriptor.builder()
                                        .name("array-structured-output-with-text")
                                        .description("Returns an array structured result plus an explicit text block")
                                        .outputSchema("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}")
                                        .build(),
                                (context, request) -> ToolResult.structured(List.of(1, 2), "summary")));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"array-structured-output-with-text","arguments":{}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response)
                    .isSuccess()
                    .hasId(2)
                    .hasContentExactly(
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("type", "text")
                                    .put("text", "summary"),
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("type", "text")
                                    .put("text", "[1,2]"));
        }
    }

    // endregion

    // region: Prompt input schema validation

    @Test
    void shouldAcceptValidPromptArguments() throws Exception {
        startServer(it -> it.json(j -> j.schemaValidator(VALIDATOR)));

        server.prompts()
                .register(
                        PromptDescriptor.of(
                                "validated-prompt",
                                "A validated prompt",
                                null,
                                List.of(PromptArgument.of("name", null, "Your name", true)),
                                PROMPT_SCHEMA),
                        List.of(PromptMessage.of(Role.USER, TextContent.of("Hello {name}"))));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"validated-prompt","arguments":{"name":"John"}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,
                "result":{"description":"A validated prompt","messages":[
                    {"role":"user","content":{"type":"text","text":"Hello {name}"}}
                  ]
                }}
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void shouldRejectPromptWithMissingRequiredField() throws Exception {
        startServer(it -> it.json(j -> j.schemaValidator(VALIDATOR)));

        server.prompts()
                .register(
                        PromptDescriptor.of(
                                "validated-prompt",
                                "A validated prompt",
                                null,
                                List.of(PromptArgument.of("name", null, "Your name", true)),
                                PROMPT_SCHEMA),
                        List.of(PromptMessage.of(Role.USER, TextContent.of("Hello {name}"))));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"validated-prompt","arguments":{}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"required property 'name' not found"}}
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    // endregion

    // region: Tool handler

    private static ToolDescriptor validatedTool() {
        return ToolDescriptor.builder()
                .name("validated")
                .description("A tool with input schema validation")
                .inputSchema(TOOL_SCHEMA)
                .build();
    }

    private static ToolDescriptor validatedTool2() {
        return ToolDescriptor.builder()
                .name("validated2")
                .description("Another tool with a distinct input schema")
                .inputSchema(JsonSchema.from(buildToolSchema2(), JsonNode.class))
                .build();
    }

    private static JsonNode buildToolSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var nameProp = props.putObject("name");
        nameProp.put("type", "string");
        var ageProp = props.putObject("age");
        ageProp.put("type", "integer");
        var req = schema.putArray("required");
        req.add("name");
        return schema;
    }

    private static JsonNode buildToolSchema2() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var emailProp = props.putObject("email");
        emailProp.put("type", "string");
        var ageProp = props.putObject("age");
        ageProp.put("type", "integer");
        var req = schema.putArray("required");
        req.add("email");
        return schema;
    }

    private static JsonNode buildPromptSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var nameProp = props.putObject("name");
        nameProp.put("type", "string");
        var req = schema.putArray("required");
        req.add("name");
        return schema;
    }

    // endregion
}
