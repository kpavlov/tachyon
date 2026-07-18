/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class StringSchemaTest extends AbstractStatelessMcpE2eTest {

    private static final String INPUT_SCHEMA_JSON =
            "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}},\"required\":[\"x\"]}";

    private static final String OUTPUT_SCHEMA_JSON =
            "{\"type\":\"object\",\"properties\":{\"y\":{\"type\":\"integer\"}},\"required\":[\"y\"]}";

    @Test
    void shouldListToolWithStringSchemas() throws Exception {
        startServer(it -> it.tool(ToolHandler.of(
                b -> b.name("string-schema-tool")
                        .description("Tool with string schemas")
                        .inputSchema(INPUT_SCHEMA_JSON)
                        .outputSchema(OUTPUT_SCHEMA_JSON),
                (ctx, args) -> ToolResult.text("ok"))));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body())
                    .inPath("$.result.tools[0].inputSchema")
                    .isObject()
                    .containsKey("properties");
            assertThatJson(response.body())
                    .inPath("$.result.tools[0].outputSchema")
                    .isObject()
                    .containsKey("properties");
        }
    }

    @Test
    void shouldHaveIdenticalResultToJsonNodeSchemas() throws Exception {
        startServer(it -> {
            it.tool(ToolHandler.of(
                    b -> b.name("from-string").description("Tool from string").inputSchema(INPUT_SCHEMA_JSON),
                    (ctx, args) -> ToolResult.text("string")));
            var mapper = new ObjectMapper();
            var jsonNodeSchema = mapper.readTree(INPUT_SCHEMA_JSON);
            it.tool(ToolHandler.of(
                    b -> b.name("from-node").description("Tool from node").inputSchema(jsonNodeSchema),
                    (ctx, args) -> ToolResult.text("node")));
        });

        try (var client = createTestClient()) {
            client.initialize();
            var r1 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """);

            assertThat(r1.statusCode()).isEqualTo(200);
            var tools = new ObjectMapper().readTree(r1.body()).path("result").path("tools");
            JsonNode fromString = null;
            JsonNode fromNode = null;
            for (var tool : tools) {
                if ("from-string".equals(tool.path("name").asString())) fromString = tool.get("inputSchema");
                if ("from-node".equals(tool.path("name").asString())) fromNode = tool.get("inputSchema");
            }
            assertThat(fromString).isNotNull();
            assertThat(fromNode).isNotNull();
            assertThat(fromString).isEqualTo(fromNode);
        }
    }

    @Test
    void shouldCallToolWithStringSchema() throws Exception {
        startServer(it ->
                it.tool("call-test", "Call test", INPUT_SCHEMA_JSON, null, (ctx, args) -> ToolResult.text("called")));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"call-test","arguments":{"x":"hello"}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body()).inPath("$.result.content[0].text").isEqualTo("called");
        }
    }
}
