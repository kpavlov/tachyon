/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class PageSizeConfigTest extends AbstractStatelessMcpE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void toolsListReturnsConfiguredPageSize() throws Exception {
        startServer(it -> it.capabilities(c -> c.tools().toolsPageSize(2))
                .tool(handler("tool-a"))
                .tool(handler("tool-b"))
                .tool(handler("tool-c")));

        try (var client = createTestClient()) {
            client.initialize();

            var page1 = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                """);
            var root1 = MAPPER.readTree(page1.body());
            var tools1 = root1.at("/result/tools");
            assertThat(tools1.isArray()).isTrue();
            assertThat(tools1.size()).isEqualTo(2);
            var cursor = root1.at("/result/nextCursor").asString(null);
            assertThat(cursor).isNotNull();

            // language=json
            var page2 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{"cursor":"%s"}}
                """.formatted(cursor));
            var root2 = MAPPER.readTree(page2.body());
            var tools2 = root2.at("/result/tools");
            assertThat(tools2.isArray()).isTrue();
            assertThat(tools2.size()).isEqualTo(1);
            assertThat(root2.at("/result/nextCursor").asString(null)).isNull();
        }
    }

    private static ToolHandler handler(String name) {
        return ToolHandler.of(
                b -> b.name(name).description("Tool " + name).inputSchema(INPUT_SCHEMA),
                (ctx, args) -> ToolResult.text("ok"));
    }

    private static final JsonNode INPUT_SCHEMA = buildInputSchema();

    private static JsonNode buildInputSchema() {
        var schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        return schema;
    }
}
