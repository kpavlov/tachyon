/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonSchema;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.testkit.McpClient;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * String-based JSON schema scenarios that hold under both MCP protocol revisions: only the client
 * creation and the response envelope differ (2026-07-28 adds {@code resultType}/{@code ttlMs}/{@code
 * cacheScope}), supplied by subclasses in {@code v2025_11_25}/{@code v2026_07_28}. Assertions use
 * {@link Option#IGNORING_EXTRA_FIELDS} where the envelope shape diverges.
 */
public abstract class AbstractStringSchemaContractTest<C extends McpClient> extends AbstractStatelessMcpE2eTest<C> {

    /** Returns a client ready to send requests (handshake already performed, if the version needs one). */
    protected abstract C readyClient() throws Exception;

    static final String INPUT_SCHEMA_JSON =
            "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}},\"required\":[\"x\"]}";

    static final String OUTPUT_SCHEMA_JSON =
            "{\"type\":\"object\",\"properties\":{\"y\":{\"type\":\"integer\"}},\"required\":[\"y\"]}";

    @Test
    void shouldListToolWithStringSchemas() throws Exception {
        startServerWith(s -> s.tools()
                .register(
                        b -> b.name("string-schema-tool")
                                .description("Tool with string schemas")
                                .inputSchema(INPUT_SCHEMA_JSON)
                                .outputSchema(OUTPUT_SCHEMA_JSON),
                        (ctx, request) -> ToolResult.text("ok")));

        try (var client = readyClient()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body()).when(Option.IGNORING_EXTRA_FIELDS).isEqualTo("""
                    {
                      "jsonrpc":"2.0",
                      "id":2,
                      "result":{"tools":[{
                        "name":"string-schema-tool",
                        "description":"Tool with string schemas",
                        "inputSchema":{
                          "type":"object",
                          "properties":{"x":{"type":"string"}},
                          "required":["x"]
                        },
                        "outputSchema":{
                          "type":"object",
                          "properties":{"y":{"type":"integer"}},
                          "required":["y"]
                        }
                      }]}
                    }
                    """);
        }
    }

    @Test
    void shouldHaveIdenticalResultToProviderBackedSchemas() throws Exception {
        startServerWith(s -> {
            s.tools()
                    .register(
                            b -> b.name("from-string")
                                    .description("Tool from string")
                                    .inputSchema(INPUT_SCHEMA_JSON),
                            (ctx, request) -> ToolResult.text("string"));
            var mapper = new ObjectMapper();
            var jsonNodeSchema = mapper.readTree(INPUT_SCHEMA_JSON);
            var providerBackedSchema = new JsonSchema() {
                @Override
                public String json() {
                    return jsonNodeSchema.toString();
                }

                @Override
                public <T> java.util.Optional<T> unwrap(Class<T> type) {
                    return type.isInstance(jsonNodeSchema)
                            ? java.util.Optional.of(type.cast(jsonNodeSchema))
                            : java.util.Optional.empty();
                }
            };
            s.tools()
                    .register(
                            b -> b.name("from-node")
                                    .description("Tool from node")
                                    .inputSchema(providerBackedSchema),
                            (ctx, request) -> ToolResult.text("node"));
        });

        try (var client = readyClient()) {
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
        startServerWith(s -> s.tools()
                .register(
                        ToolDescriptor.builder()
                                .name("call-test")
                                .description("Call test")
                                .inputSchema(INPUT_SCHEMA_JSON)
                                .build(),
                        (ctx, request) -> ToolResult.text("called")));

        try (var client = readyClient()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"call-test","arguments":{"x":"hello"}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response).isSuccess().hasTextContent("called");
        }
    }
}
