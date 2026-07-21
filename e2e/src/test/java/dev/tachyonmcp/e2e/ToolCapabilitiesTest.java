/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class ToolCapabilitiesTest extends AbstractStatelessMcpE2eTest {

    // region: Output Schema Tests

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
            output-schema-tool | true  | object
            simple             | false |
            """)
    void shouldIncludeOutputSchema(String toolName, boolean hasSchema, String schemaType) throws Exception {
        ToolHandler handler;
        if (hasSchema) {
            handler = outputSchemaToolHandler(OUTPUT_SCHEMA);
        } else {
            handler = simpleToolHandler(toolName, "A " + toolName + " tool");
        }
        startServer(it -> it.tool(handler));

        try (var client = createTestClient()) {
            var response = listTools(client);

            final String expected;
            if (hasSchema) {
                // language=JSON
                expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "tools": [
                          {
                            "name": "output-schema-tool",
                            "description": "A tool with output schema",
                            "inputSchema": {
                              "type": "object",
                              "properties": {
                                "message": { "type": "string", "description": "Input" }
                              },
                              "required": ["message"]
                            },
                            "outputSchema": {
                              "type": "object",
                              "properties": {
                                "result": { "type": "string", "description": "The output result" }
                              }
                            }
                          }
                        ]
                      }
                    }
                    """;
            } else {
                // language=JSON
                expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "tools": [
                          {
                            "name": "%s",
                            "description": "A %s tool",
                            "inputSchema": {
                              "type": "object",
                              "properties": {
                                "message": { "type": "string", "description": "Input" }
                              },
                              "required": ["message"]
                            }
                          }
                        ]
                      }
                    }
                    """.formatted(toolName, toolName);
            }
            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void shouldIncludeMultipleToolsWithMixedOutputSchemas() throws Exception {
        startServer(it -> it.tool(simpleToolHandler("tool-a", "Tool A"))
                .tool(outputSchemaToolHandler(OUTPUT_SCHEMA))
                .tool(simpleToolHandler("tool-b", "Tool B")));

        try (var client = createTestClient()) {
            var response = listTools(client);

            var mapper = new ObjectMapper();
            var root = mapper.readTree(response.body());
            var tools = root.at("/result/tools");
            assertThat(tools).isNotNull();
            assertThat(tools.size()).isEqualTo(3);

            for (var tool : tools) {
                var name = tool.get("name").asString();
                var hasOutputSchema = tool.has("outputSchema");
                if ("output-schema-tool".equals(name)) {
                    assertThat(hasOutputSchema).isTrue();
                    assertThat(tool.get("outputSchema").get("type").asString()).isEqualTo("object");
                } else {
                    assertThat(hasOutputSchema).isFalse();
                }
            }
        }
    }

    // endregion

    // region: Execution / Task Support Tests

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void shouldIncludeExecutionTaskSupport(String toolName, boolean hasExecution, ToolHandler handler)
            throws Exception {
        startServer(it -> it.tool(handler));

        try (var client = createTestClient()) {
            var response = listTools(client);

            assertThatJson(response.body()).inPath("$.result.tools[0].name").isEqualTo(toolName);
            if (hasExecution) {
                assertThatJson(response.body())
                        .inPath("$.result.tools[0].execution.taskSupport")
                        .isEqualTo("optional");
            } else {
                assertThatJson(response.body())
                        .inPath("$.result.tools[0]")
                        .isObject()
                        .doesNotContainKey("execution");
            }
        }
    }

    static Stream<Arguments> shouldIncludeExecutionTaskSupport() {
        return Stream.of(
                Arguments.of("task-aware-tool", true, taskAwareToolHandler(TaskSupport.OPTIONAL)),
                Arguments.of("simple", false, simpleToolHandler("simple", "A simple tool")));
    }

    // endregion

    // region: Descriptor Registration Tests

    @Test
    void shouldRegisterWithMinimalDescriptor() throws Exception {
        startEmptyServer();
        server.tools().register(ToolHandler.of("minimal-tool", (ctx, request) -> ToolResult.text("ok")));

        try (var client = createTestClient()) {
            var response = listTools(client);

            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"minimal-tool", "inputSchema":{"type":"object"}}]}}
                """;
            assertThatJson(response.body()).isEqualTo(expected.trim());
        }
    }

    // region: Structured content in tools/call

    @Test
    void shouldReturnStructuredContentAndTextFallback() throws Exception {
        startServer(it -> it.tool(structuredToolHandler()));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"structured","arguments":{"message":"hi"}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("Echo: hi");
            assertThatJson(response.body())
                    .inPath("$.result.structuredContent")
                    .isObject()
                    .containsKey("echo");
        }
    }

    // endregion

    @Test
    void shouldRegisterWithFullDescriptor() throws Exception {
        var annotations = ToolAnnotations.of(null, true, false, null, null);
        startEmptyServer();
        server.tools()
                .register(ToolHandler.of(
                        b -> b.name("full-tool")
                                .title("Full Tool")
                                .description("A tool with all metadata")
                                .inputSchema(INPUT_SCHEMA)
                                .outputSchema(OUTPUT_SCHEMA)
                                .taskSupport(TaskSupport.OPTIONAL)
                                .annotations(annotations),
                        (ctx, request) -> ToolResult.text("ok")));

        try (var client = createTestClient()) {
            var response = listTools(client);

            var expected = """
                {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"full-tool","title":"Full Tool","description":"A tool with all metadata","inputSchema":{"type":"object","properties":{"message":{"type":"string","description":"Input"}},"required":["message"]},"outputSchema":{"type":"object","properties":{"result":{"type":"string","description":"The output result"}}},"execution":{"taskSupport":"optional"},"annotations":{"readOnlyHint":true,"destructiveHint":false}}]}}
                """;
            assertThatJson(response.body()).isEqualTo(expected.trim());
        }
    }

    // endregion

    // region: Tool Handler Implementations

    private static HttpResponse<String> listTools(TestMcpClient client) throws Exception {
        client.initialize();
        return client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);
    }

    private static ToolHandler outputSchemaToolHandler(JsonNode outputSchemaNode) {
        return ToolHandler.of(
                b -> b.name("output-schema-tool")
                        .description("A tool with output schema")
                        .inputSchema(INPUT_SCHEMA)
                        .outputSchema(outputSchemaNode),
                (ctx, request) -> ToolResult.text("ok"));
    }

    private static ToolHandler simpleToolHandler(String name, String description) {
        return ToolHandler.of(
                b -> b.name(name).description(description).inputSchema(INPUT_SCHEMA),
                (ctx, request) -> ToolResult.text("ok"));
    }

    private static ToolHandler taskAwareToolHandler(TaskSupport taskSupport) {
        return ToolHandler.of(
                b -> b.name("task-aware-tool")
                        .description("A task-aware tool")
                        .inputSchema(INPUT_SCHEMA)
                        .taskSupport(taskSupport),
                (ctx, request) -> ToolResult.text("ok"));
    }

    private static ToolHandler structuredToolHandler() {
        return ToolHandler.of(
                b -> b.name("structured")
                        .description("Returns structured content")
                        .inputSchema(INPUT_SCHEMA),
                (ctx, request) -> {
                    var msg = request.arguments().stringValue("message");
                    var echo = JsonNodeFactory.instance.objectNode().put("echo", msg);
                    return ToolResult.of(echo, "Echo: " + msg);
                });
    }

    // ---- JSON schemas ----

    private static final JsonNode OUTPUT_SCHEMA = buildOutputSchema();

    private static JsonNode buildOutputSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var result = props.putObject("result");
        result.put("type", "string");
        result.put("description", "The output result");
        return schema;
    }

    private static final JsonNode INPUT_SCHEMA = buildInputSchema();

    private static JsonNode buildInputSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var msg = props.putObject("message");
        msg.put("type", "string");
        msg.put("description", "Input");
        var req = schema.putArray("required");
        req.add("message");
        return schema;
    }
}
