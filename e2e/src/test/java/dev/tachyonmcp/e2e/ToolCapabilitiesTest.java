/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.domain.ToolAnnotations;
import dev.tachyonmcp.server.features.tasks.TaskSupport;
import dev.tachyonmcp.server.features.tools.SyncToolHandler;
import dev.tachyonmcp.server.features.tools.ToolArgs;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class ToolCapabilitiesTest extends AbstractMcpE2eTest {

    // region: Output Schema Tests

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
            output-schema-tool | true  | object
            simple             | false |
            """)
    void shouldIncludeOutputSchema(String toolName, boolean hasSchema, String schemaType) throws Exception {
        ToolHandler handler;
        if (hasSchema) {
            handler = new OutputSchemaToolHandler(OUTPUT_SCHEMA);
        } else {
            handler = new SimpleToolHandler(toolName, "A " + toolName + " tool");
        }
        startServer(it -> it.tool(handler));

        try (var client = createTestClient()) {

            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);

            if (hasSchema) {
                // language=JSON
                var expected = """
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
                assertThatJson(response.body()).isEqualTo(expected);
            } else {
                // language=JSON
                var expected = """
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
                assertThatJson(response.body()).isEqualTo(expected);
            }
        }
    }

    @Test
    void shouldIncludeMultipleToolsWithMixedOutputSchemas() throws Exception {
        startServer(it -> it.tool(new SimpleToolHandler("tool-a", "Tool A"))
                .tool(new OutputSchemaToolHandler(OUTPUT_SCHEMA))
                .tool(new SimpleToolHandler("tool-b", "Tool B")));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);

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
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);

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
                Arguments.of("task-aware-tool", true, new TaskAwareToolHandler(TaskSupport.OPTIONAL)),
                Arguments.of("simple", false, new SimpleToolHandler("simple", "A simple tool")));
    }

    // endregion

    // region: Descriptor Registration Tests

    @Test
    void shouldRegisterWithMinimalDescriptor() throws Exception {
        startEmptyServer();
        server.registerTool(new SyncToolHandler() {
            @Override
            public String name() {
                return "minimal-tool";
            }

            @Override
            public ToolResult handle(InteractionContext context, ToolArgs arguments) {
                return ToolResult.text("ok");
            }
        });

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);

            // language=JSON
            var expected = """
                {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"minimal-tool", "inputSchema":{"type":"object"}}]}}
                """;
            assertThatJson(response.body()).isEqualTo(expected.trim());
        }
    }

    @Test
    void shouldRegisterWithFullDescriptor() throws Exception {
        var annotations = ToolAnnotations.of(null, true, false, null, null);
        startEmptyServer();
        server.registerTool(new SyncToolHandler() {
            @Override
            public String name() {
                return "full-tool";
            }

            @Override
            public String title() {
                return "Full Tool";
            }

            @Override
            public String description() {
                return "A tool with all metadata";
            }

            @Override
            public JsonNode inputSchema() {
                return INPUT_SCHEMA;
            }

            @Override
            public JsonNode outputSchema() {
                return OUTPUT_SCHEMA;
            }

            @Override
            public TaskSupport taskSupport() {
                return TaskSupport.OPTIONAL;
            }

            @Override
            public ToolAnnotations annotations() {
                return annotations;
            }

            @Override
            public ToolResult handle(InteractionContext context, ToolArgs arguments) {
                return ToolResult.text("ok");
            }
        });

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);

            var expected = """
                {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"full-tool","title":"Full Tool","description":"A tool with all metadata","inputSchema":{"type":"object","properties":{"message":{"type":"string","description":"Input"}},"required":["message"]},"outputSchema":{"type":"object","properties":{"result":{"type":"string","description":"The output result"}}},"execution":{"taskSupport":"optional"},"annotations":{"readOnlyHint":true,"destructiveHint":false}}]}}
                """;
            assertThatJson(response.body()).isEqualTo(expected.trim());
        }
    }

    // endregion

    // region: Tool Handler Implementations

    private record OutputSchemaToolHandler(JsonNode outputSchemaNode) implements SyncToolHandler {
        @Override
        public String name() {
            return "output-schema-tool";
        }

        @Override
        public String description() {
            return "A tool with output schema";
        }

        @Override
        public JsonNode inputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public JsonNode outputSchema() {
            return outputSchemaNode;
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolArgs arguments) {
            return ToolResult.text("ok");
        }
    }

    private record SimpleToolHandler(String name, String description) implements SyncToolHandler {
        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public JsonNode inputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolArgs arguments) {
            return ToolResult.text("ok");
        }
    }

    private record TaskAwareToolHandler(TaskSupport taskSupport) implements SyncToolHandler {
        @Override
        public String name() {
            return "task-aware-tool";
        }

        @Override
        public String description() {
            return "A task-aware tool";
        }

        @Override
        public JsonNode inputSchema() {
            return INPUT_SCHEMA;
        }

        @Override
        public TaskSupport taskSupport() {
            return taskSupport;
        }

        @Override
        public ToolResult handle(InteractionContext context, ToolArgs arguments) {
            return ToolResult.text("ok");
        }
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
