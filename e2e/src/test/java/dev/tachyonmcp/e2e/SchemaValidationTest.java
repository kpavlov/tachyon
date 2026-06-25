/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.JsonSchemaValidator;
import dev.tachyonmcp.server.NetworkntJsonSchemaValidator;
import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.McpContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class SchemaValidationTest extends AbstractMcpE2eTest {

    private static final JsonSchemaValidator VALIDATOR = new NetworkntJsonSchemaValidator();

    private static final JsonNode TOOL_SCHEMA = buildToolSchema();
    private static final JsonNode PROMPT_SCHEMA = buildPromptSchema();

    // region: Tool input schema validation

    @Test
    void shouldValidateMultipleToolsWithDistinctSchemas() throws Exception {
        startServer(it -> it.jsonSchemaValidator(VALIDATOR)
                .tool(new ValidatedToolHandler())
                .tool(new ValidatedToolHandler2()));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();

            var r1 = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"validated","arguments":{"name":"John","age":30}}}
                """);
            assertThat(r1.statusCode()).isEqualTo(200);
            assertThatJson(r1.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"ok"}]}}
                """);

            var r2 = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"validated2","arguments":{"email":"john@example.com","age":25}}}
                """);
            assertThat(r2.statusCode()).isEqualTo(200);
            assertThatJson(r2.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"ok"}]}}
                """);

            var r3 = client.sendRequest(sessionId, """
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
        startServer(it -> it.jsonSchemaValidator(VALIDATOR).tool(new ValidatedToolHandler()));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
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
        startServer(it -> it.jsonSchemaValidator(VALIDATOR).tool(new ValidatedToolHandler()));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"validated","arguments":{"age":30}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,"error":{"code":-32600,"message":"required property 'name' not found"}}
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void shouldRejectToolCallWithWrongType() throws Exception {
        startServer(it -> it.jsonSchemaValidator(VALIDATOR).tool(new ValidatedToolHandler()));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"validated","arguments":{"name":123}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);

            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,"error":{"code":-32600,"message":"integer found, string expected"}}
                """;
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    // endregion

    // region: Prompt input schema validation

    @Test
    void shouldAcceptValidPromptArguments() throws Exception {
        startServer(it -> it.jsonSchemaValidator(VALIDATOR));

        server.prompts()
                .add(
                        PromptDescriptor.of(
                                "validated-prompt",
                                "A validated prompt",
                                null,
                                List.of(PromptArgument.of("name", null, "Your name", true)),
                                PROMPT_SCHEMA),
                        List.of(dev.tachyonmcp.server.domain.PromptMessage.of(
                                Role.USER, TextContent.of("Hello {name}"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
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
        startServer(it -> it.jsonSchemaValidator(VALIDATOR));

        server.prompts()
                .add(
                        PromptDescriptor.of(
                                "validated-prompt",
                                "A validated prompt",
                                null,
                                List.of(PromptArgument.of("name", null, "Your name", true)),
                                PROMPT_SCHEMA),
                        List.of(dev.tachyonmcp.server.domain.PromptMessage.of(
                                Role.USER, TextContent.of("Hello {name}"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
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

    private static class ValidatedToolHandler implements SyncToolHandler<Object, ToolResult> {

        @Override
        public String name() {
            return "validated";
        }

        @Override
        public String description() {
            return "A tool with input schema validation";
        }

        @Override
        public JsonNode inputSchema() {
            return TOOL_SCHEMA;
        }

        @Override
        public ToolResult handle(McpContext context, Object arguments) {
            return ToolResult.text("ok");
        }
    }

    // endregion

    // region: Schema builders

    private static class ValidatedToolHandler2 implements SyncToolHandler<Object, ToolResult> {

        @Override
        public String name() {
            return "validated2";
        }

        @Override
        public String description() {
            return "Another tool with a distinct input schema";
        }

        @Override
        public JsonNode inputSchema() {
            return buildToolSchema2();
        }

        @Override
        public ToolResult handle(McpContext context, Object arguments) {
            return ToolResult.text("ok");
        }
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
