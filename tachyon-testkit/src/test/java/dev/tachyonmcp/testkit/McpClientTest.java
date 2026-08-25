/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies {@link McpClient#extractJsonRpcResponse(String, String)} matches ids structurally. */
class McpClientTest {

    @Test
    void matchesIdDespiteWhitespaceAfterColon() {
        var sseBody = """
                data: {"jsonrpc":"2.0","method":"notifications/progress","params":{}}

                data: {"jsonrpc":"2.0", "id": 7, "result":{"ok":true}}

                """;

        assertThat(McpClient.extractJsonRpcResponse(sseBody, "7")).contains("\"ok\":true");
    }

    @Test
    void doesNotMatchIdAsSubstringOfALongerId() {
        var sseBody = """
                data: {"jsonrpc":"2.0","id":70,"result":{"which":"wrong"}}

                data: {"jsonrpc":"2.0","id":7,"result":{"which":"right"}}

                """;

        assertThat(McpClient.extractJsonRpcResponse(sseBody, "7")).contains("\"which\":\"right\"");
    }

    @Test
    void isNotFooledByALaterNotificationContainingTheIdAsAFieldValue() {
        var sseBody = """
                data: {"jsonrpc":"2.0","id":7,"result":{"which":"right"}}

                data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"id":7}}

                """;

        assertThat(McpClient.extractJsonRpcResponse(sseBody, "7")).contains("\"which\":\"right\"");
    }

    @Test
    void matchesStringIds() {
        var sseBody = """
                data: {"jsonrpc":"2.0","id":"tasks-get","result":{"ok":true}}

                """;

        assertThat(McpClient.extractJsonRpcResponse(sseBody, "\"tasks-get\"")).contains("\"ok\":true");
    }
}
