/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.json.JsonSchemaValidator;
import dev.tachyonmcp.server.json.NetworkntJsonSchemaValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class SchemaValidationTest extends AbstractStatelessMcpE2eTest {

    private static final JsonSchemaValidator VALIDATOR = new NetworkntJsonSchemaValidator();

    private static final JsonNode TOOL_SCHEMA = buildToolSchema();
    private static final JsonNode PROMPT_SCHEMA = buildPromptSchema();

    // region: Tool input schema validation

    @Test
    void shouldValidateMultipleToolsWithDistinctSchemas() throws Exception {
        startServer(it -> it.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR))
                .tool(validatedTool())
                .tool(validatedTool2()));

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
        startServer(it -> it.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR))
                .tool(validatedTool()));

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
        startServer(it -> it.json(j -> j.inputSchemaValidator(VALIDATOR).outputSchemaValidator(VALIDATOR))
                .tool(validatedTool()));

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
        startServer(it -> it.json(j -> j.schemaValidator(VALIDATOR)).tool(validatedTool()));

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
                        List.of(dev.tachyonmcp.server.domain.PromptMessage.of(
                                Role.USER, TextContent.of("Hello {name}"))));

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
            assertThatJson(response.body()).inPath("$.result").isObject().containsKey("messages");
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
                        List.of(dev.tachyonmcp.server.domain.PromptMessage.of(
                                Role.USER, TextContent.of("Hello {name}"))));

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

    private static ToolHandler validatedTool() {
        return ToolHandler.of(
                b -> b.name("validated")
                        .description("A tool with input schema validation")
                        .inputSchema(TOOL_SCHEMA),
                (context, request) -> ToolResult.text("ok"));
    }

    private static ToolHandler validatedTool2() {
        return ToolHandler.of(
                b -> b.name("validated2")
                        .description("Another tool with a distinct input schema")
                        .inputSchema(buildToolSchema2()),
                (context, request) -> ToolResult.text("ok"));
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
