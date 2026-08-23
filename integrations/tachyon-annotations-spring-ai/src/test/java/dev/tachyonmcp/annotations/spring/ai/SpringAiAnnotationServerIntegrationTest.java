/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.annotations.spring.ai;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.testkit.McpTestClients;
import dev.tachyonmcp.testkit.McpTestServers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * Starts a real Tachyon server (Netty transport, port 0) with {@link SpringAiAnnotationProvider}
 * wired through {@code ServerBuilder.annotations(...)} and exercises the annotated handlers over
 * the MCP JSON-RPC wire protocol. Spec ref: MCP 2025-11-25 tools/resources/prompts.
 */
class SpringAiAnnotationServerIntegrationTest {

    @SuppressWarnings("unused")
    static class Fixture {

        @McpTool(name = "greet", description = "Greets someone")
        String greet(@McpToolParam(description = "target name") String who) {
            return "Hello, " + who + "!";
        }

        @McpResource(uri = "test://config", name = "cfg")
        String config() {
            return "static-config";
        }

        @McpResource(uri = "test://item/{id}", name = "item")
        String item(String id) {
            return "item-" + id;
        }

        @McpPrompt(name = "story", description = "Tells a story")
        String story(String topic) {
            return "A story about " + topic;
        }
    }

    private static TachyonServer startServer() {
        return McpTestServers.start(
                b -> b.annotations(annotations -> {
                    annotations.provider(new SpringAiAnnotationProvider());
                    annotations.register(new Fixture());
                }),
                server -> {});
    }

    @Test
    void annotatedToolIsAdvertisedAndCallableOverWire() throws Exception {
        try (var server = startServer();
                var client = McpTestClients.latest(server.port())) {
            var list = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                    """);

            // language=json
            var expectedList = """
                    {"jsonrpc":"2.0","id":1,"result":{
                        "tools":[{
                            "name":"greet",
                            "description":"Greets someone",
                            "inputSchema":{"type":"object",
                                "properties":{"who":{"type":"string","description":"target name"}},
                                "required":["who"]},
                            "annotations":{"readOnlyHint":false,
                                "destructiveHint":true,
                                "idempotentHint":false,
                                "openWorldHint":true}
                        }],
                        "resultType":"complete","ttlMs":0,"cacheScope":"public"}
                    }
                    """;
            assertThatJson(list.body()).isEqualTo(expectedList);

            var call = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"tools/call",
                     "params":{"name":"greet","arguments":{"who":"Ada"}}}
                    """);

            // language=json
            var expectedCall = """
                    {"jsonrpc":"2.0","id":2,"result":{
                        "content":[{"type":"text","text":"Hello, Ada!"}],
                        "resultType":"complete"}
                    }
                    """;
            assertThatJson(call.body()).isEqualTo(expectedCall);
        }
    }

    @Test
    void staticAndTemplatedResourcesAreReadableOverWire() throws Exception {
        try (var server = startServer();
                var client = McpTestClients.latest(server.port())) {
            var read = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"resources/read",
                     "params":{"uri":"test://config"}}
                    """);

            // language=json
            var expectedRead = """
                    {"jsonrpc":"2.0","id":1,"result":{
                        "contents":[{"uri":"test://config","text":"static-config"}],
                        "resultType":"complete","ttlMs":0,"cacheScope":"public"}
                    }
                    """;
            assertThatJson(read.body()).isEqualTo(expectedRead);

            var templated = client.post("""
                    {"jsonrpc":"2.0","id":2,"method":"resources/read",
                     "params":{"uri":"test://item/42"}}
                    """);

            // language=json
            var expectedTemplated = """
                    {"jsonrpc":"2.0","id":2,"result":{
                        "contents":[{"uri":"test://item/42","text":"item-42"}],
                        "resultType":"complete","ttlMs":0,"cacheScope":"public"}
                    }
                    """;
            assertThatJson(templated.body()).isEqualTo(expectedTemplated);
        }
    }

    @Test
    void annotatedPromptReturnsMessagesOverWire() throws Exception {
        try (var server = startServer();
                var client = McpTestClients.latest(server.port())) {
            var get = client.post("""
                    {"jsonrpc":"2.0","id":1,"method":"prompts/get",
                     "params":{"name":"story","arguments":{"topic":"cats"}}}
                    """);

            // language=json
            var expected = """
                    {"jsonrpc":"2.0","id":1,"result":{
                        "description":"Tells a story",
                        "messages":[{"role":"user",
                            "content":{"type":"text","text":"A story about cats"}}],
                        "resultType":"complete"}
                    }
                    """;
            assertThatJson(get.body()).isEqualTo(expected);
        }
    }
}
