/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.langchain4j;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.testkit.McpTestClients;
import dev.tachyonmcp.testkit.McpTestServers;
import org.junit.jupiter.api.Test;

/**
 * Starts a real Tachyon server (Netty transport, port 0) with {@link LangChain4jAnnotationProvider}
 * wired through {@code ServerBuilder.annotations(...)} and exercises the annotated handlers over
 * the MCP JSON-RPC wire protocol. Spec ref: MCP 2025-11-25 tools.
 */
class LangChain4jAnnotationServerIntegrationTest {

    @SuppressWarnings("unused")
    static class Fixture {

        @Tool("Adds numbers")
        int add(@P(name = "a", description = "first operand") int a, @P(value = "second", required = false) Integer b) {
            return a + (b == null ? 0 : b);
        }
    }

    private static TachyonServer startServer() {
        return McpTestServers.start(
                b -> b.annotations(annotations -> {
                    annotations.provider(new LangChain4jAnnotationProvider());
                    annotations.register(new Fixture());
                }),
                server -> {});
    }

    @Test
    void annotatedToolIsAdvertisedWithSchemaAndCallable() throws Exception {
        try (var server = startServer();
                var client = McpTestClients.latest(server.port())) {
            var list = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                    """);

            // language=json
            var expectedList = """
                    {"jsonrpc":"2.0","id":1,"result":{
                        "tools":[{
                            "name":"add",
                            "description":"Adds numbers",
                            "inputSchema":{"type":"object",
                                "properties":{
                                    "a":{"type":"integer","description":"first operand"},
                                    "b":{"type":"integer","description":"second"}},
                                "required":["a"]}
                        }],
                        "resultType":"complete","ttlMs":0,"cacheScope":"public"}
                    }
                    """;
            assertThatJson(list.body()).isEqualTo(expectedList);

            var call = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call",
                     "params":{"name":"add","arguments":{"a":3,"b":4}}}
                    """);

            // language=json
            var expectedCall = """
                    {"jsonrpc":"2.0","id":2,"result":{
                        "content":[{"type":"text","text":"7"}],
                        "resultType":"complete"}
                    }
                    """;
            assertThatJson(call.body()).isEqualTo(expectedCall);
        }
    }

    @Test
    void optionalArgumentMayBeOmittedOnTheWire() throws Exception {
        try (var server = startServer();
                var client = McpTestClients.latest(server.port())) {
            var call = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"add","arguments":{"a":5}}}
                    """);

            // language=json
            var expected = """
                    {"jsonrpc":"2.0","id":1,"result":{
                        "content":[{"type":"text","text":"5"}],
                        "resultType":"complete"}
                    }
                    """;
            assertThatJson(call.body()).isEqualTo(expected);
        }
    }
}
