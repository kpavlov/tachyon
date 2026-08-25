/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp20260728;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonDocument;
import dev.tachyonmcp.api.server.domain.TaskResult;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.features.tasks.TasksExtension;
import dev.tachyonmcp.core.server.json.JsonUtils;
import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 permits any JSON value (object, array, or scalar) as {@code outputSchema}'s root
 * and as {@code structuredContent} -- unlike 2025-11-25, which restricts both to a JSON object (see
 * {@code dev.tachyonmcp.e2e.SchemaValidationTest} and the sibling 2025-11-25 fallback coverage for
 * that restriction). These tests cover the array/scalar shapes end to end under 2026-07-28.
 */
class StructuredOutputSchemaShapeTest extends AbstractStatelessMcpE2eTest {

    // language=JSON
    private static final String NO_ARGS_SCHEMA = "{\"type\": \"object\", \"properties\": {}}";

    @Test
    void shouldAcceptArrayRootOutputSchemaAndReturnArrayStructuredContent() throws Exception {
        startServerWith(s -> s.tools()
                .register(
                        tool -> tool.name("array_output")
                                .description("Returns an array structured result")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .outputSchema("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}"),
                        (ctx, request) -> ToolResult.structured(JsonDocument.of("[1,2,3]"))));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"array_output","arguments":{}}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThat(response)
                    .isSuccess()
                    .hasId(1)
                    .hasTextContent("[1,2,3]")
                    .hasStructuredContent("[1,2,3]")
                    .hasResultType("complete");
        }
    }

    @Test
    void shouldAcceptScalarRootOutputSchemaAndReturnScalarStructuredContent() throws Exception {
        startServerWith(s -> s.tools()
                .register(
                        tool -> tool.name("scalar_output")
                                .description("Returns a scalar structured result")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .outputSchema("{\"type\":\"string\"}"),
                        (ctx, request) -> ToolResult.structured(JsonDocument.of("\"hello\""))));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"scalar_output","arguments":{}}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThat(response)
                    .isSuccess()
                    .hasId(1)
                    .hasTextContent("\"hello\"")
                    .hasStructuredContent("\"hello\"")
                    .hasResultType("complete");
        }
    }

    @Test
    void shouldRejectStructuredOutputThatDoesNotMatchArraySchema() throws Exception {
        startServerWith(s -> s.tools()
                .register(
                        tool -> tool.name("bad_array_output")
                                .description("Declares an array outputSchema but returns an object")
                                .inputSchema(NO_ARGS_SCHEMA)
                                .outputSchema("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}"),
                        (ctx, request) -> ToolResult.structured(JsonDocument.of("{\"oops\":true}"))));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"bad_array_output","arguments":{}}}
                    """);

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThat(response).isSuccess().hasId(1).isToolError().hasTextContent("object found, array expected");
        }
    }

    @Test
    void shouldInlineArrayStructuredContentThroughTasksGet() throws Exception {
        startServer(builder -> builder.withExtensions(TasksExtension.instance()), registrar -> {});
        var task = server.tasks().create();
        task.complete(TaskResult.completed(JsonUtils.parse("[1,2,3]")));

        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tasks/get","params":{"taskId":"%s",\
                    "_meta":{"io.modelcontextprotocol/clientCapabilities":{"extensions":{"%s":{}}}}}}
                    """.formatted(task.id(), TasksExtension.ID));

            // JsonRpcResponseAssert's success helpers assert result.structuredContent/content -- this
            // response nests the tool result one level deeper (result.result.*), so it isn't a fit.
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThatJson(response.body())
                    .whenIgnoringPaths("$.result.createdAt", "$.result.lastUpdatedAt")
                    .isEqualTo("""
                            {
                              "jsonrpc":"2.0",
                              "id":2,
                              "result":{
                                "taskId":"%s",
                                "status":"completed",
                                "ttlMs":null,
                                "resultType":"complete",
                                "result":{
                                  "content":[{"type":"text","text":"[1,2,3]"}],
                                  "structuredContent":[1,2,3],
                                  "resultType":"complete"
                                }
                              }
                            }
                            """.formatted(task.id()));
        }
    }
}
