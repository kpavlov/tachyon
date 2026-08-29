/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp.v2026_07_28;

import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.mcp.AbstractToolErrorContractTest;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.McpTestClients;
import org.junit.jupiter.api.Test;

class ToolErrorContractTest extends AbstractToolErrorContractTest<Mcp20260728Client> {

    @Override
    protected Mcp20260728Client createTestClient() {
        return createTestClient(port);
    }

    @Override
    protected Mcp20260728Client createTestClient(int port) {
        return McpTestClients.latest(port);
    }

    @Override
    protected Mcp20260728Client readyClient() {
        return createModernTestClient();
    }

    @Test
    protected void shouldRedactIllegalArgumentExceptionFromInvalidParamsError() throws Exception {
        startServerWith(s -> s.tools()
                .register(builder -> builder.name("bad-arg").description("Rejects input"), (context, request) -> {
                    throw new IllegalArgumentException("sensitive internal detail");
                }));

        try (var client = readyClient()) {
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"bad-arg","arguments":{}}}
                """);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThatResponse(response)
                    .isJsonRpcError()
                    .hasId(2)
                    .hasErrorCode(-32602)
                    .hasErrorMessage("Invalid params");
            assertThat(response.body()).doesNotContain("sensitive internal detail");
        }
    }
}
