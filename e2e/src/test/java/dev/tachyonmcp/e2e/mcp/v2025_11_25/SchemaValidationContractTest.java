/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2025_11_25;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.json.JsonSchemaValidator;
import dev.tachyonmcp.api.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import dev.tachyonmcp.core.server.json.NetworkntJsonSchemaValidator;
import dev.tachyonmcp.e2e.mcp.AbstractSchemaValidationContractTest;
import dev.tachyonmcp.testkit.Mcp20251125Client;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 2025-11-25 schema validation contract: shared scenarios plus version-specific structured content
 * tests. Under 2025-11-25, {@code structuredContent} is object-only on the wire, so array results
 * fall back to the text block instead of being sent as structured content.
 */
class SchemaValidationContractTest extends AbstractSchemaValidationContractTest<Mcp20251125Client> {

    private static final JsonSchemaValidator VALIDATOR = new NetworkntJsonSchemaValidator();

    @Override
    protected Mcp20251125Client createTestClient() {
        return createTestClient(port);
    }

    @Override
    protected Mcp20251125Client createTestClient(int port) {
        return new Mcp20251125Client(port);
    }

    @Override
    protected Mcp20251125Client readyClient() throws Exception {
        var client = createTestClient();
        client.initialize();
        return client;
    }

    @Test
    void shouldFallBackToTextOnlyForArrayStructuredContentUnder20251125() throws Exception {
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

        try (var client = readyClient()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"array-structured-output","arguments":{}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response).isSuccess().hasId(2).hasTextContent("[1,2,3]").doesNotHaveStructuredContent();
        }
    }

    @Test
    void shouldEmitBothHandlerTextAndArrayFallbackTextUnder20251125() throws Exception {
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

        try (var client = readyClient()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"array-structured-output-with-text","arguments":{}}}
                """);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response)
                    .isSuccess()
                    .hasId(2)
                    .doesNotHaveStructuredContent()
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
}
