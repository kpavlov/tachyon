/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ClientCapabilities;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.runtime.InteractionContext;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.features.tools.ToolDescriptor;
import dev.tachyonmcp.server.features.tools.ToolHandler;
import dev.tachyonmcp.server.features.tools.ToolRequest;
import dev.tachyonmcp.server.features.tools.ToolResult;
import dev.tachyonmcp.server.session.DispatchContext;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class ExtensionsTest extends AbstractMcpE2eTest {

    private static final String TEST_EXT_ID = "com.example/test";

    @Test
    void serverAdvertisesExtensionInCapabilities() throws Exception {
        startServer(it -> it.extension(new TestExtension()));

        try (var client = createTestClient()) {
            // Send initialize with matching extension
            var initBody = buildInitializeJson(Map.of(TEST_EXT_ID, JsonNodeFactory.instance.objectNode()));
            var response = client.post(null, initBody);
            assertThatJson(response.body())
                    .inPath("$.result.capabilities.extensions")
                    .isObject()
                    .containsKey(TEST_EXT_ID);
        }
    }

    @Test
    void extensionNotAdvertisedWhenClientDoesNotDeclare() throws Exception {
        startServer(it -> it.extension(new TestExtension()));

        try (var client = createTestClient()) {
            var initBody = buildInitializeJson(Map.of());
            var response = client.post(null, initBody);
            assertThatJson(response.body())
                    .inPath("$.result.capabilities")
                    .isObject()
                    .doesNotContainKey("extensions");
        }
    }

    @Test
    void extensionEnabledWhenClientDeclaresIt() throws Exception {
        startServer(it -> it.extension(new TestExtension()));

        try (var client = createTestClient()) {
            var initBody = buildInitializeJson(Map.of(TEST_EXT_ID, JsonNodeFactory.instance.objectNode()));
            var response = client.post(null, initBody);
            String body = response.body();
            assertThatJson(body)
                    .inPath("$.result.capabilities.extensions")
                    .isObject()
                    .containsKey(TEST_EXT_ID);
        }
    }

    @Test
    void extensionMethodRequiresMetaEnvelope() throws Exception {
        startServer(it -> it.extension(new TestExtension()));

        try (var client = createTestClient()) {
            var initBody = buildInitializeJson(Map.of(TEST_EXT_ID, JsonNodeFactory.instance.objectNode()));
            var response = client.post(null, initBody);
            var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
            client.sendInitialized(sessionId);

            // Call extension method WITHOUT meta envelope -> should fail
            // language=JSON
            var callWithoutMeta = """
                    {"jsonrpc":"2.0","id":2,"method":"test/ext-call","params":{}}
                    """;
            var resp1 = client.sendRequest(sessionId, callWithoutMeta);
            assertThat(resp1.body()).contains("-32601");

            // Call extension method WITH meta envelope -> should succeed
            // language=JSON
            var callWithMeta = """
                    {"jsonrpc":"2.0","id":3,"method":"test/ext-call","params":{"_meta":{"com.example/test":{}}}}
                    """;
            var resp2 = client.sendRequest(sessionId, callWithMeta);
            assertThatJson(resp2.body()).inPath("$.result.status").isEqualTo("ok");
        }
    }

    @Test
    void extensionToolInvisibleWhenNotNegotiated() throws Exception {
        startServer(it -> it.extension(new TestExtensionWithTool()));

        try (var client = createTestClient()) {
            var response = client.post(null, buildInitializeJson(Map.of()));
            var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
            client.sendInitialized(sessionId);

            var listResp = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                    """);
            assertThatJson(listResp.body()).inPath("$.result.tools").isArray().isEmpty();
        }
    }

    @Test
    void extensionToolVisibleAndCallableWhenNegotiated() throws Exception {
        startServer(it -> it.extension(new TestExtensionWithTool()));

        try (var client = createTestClient()) {
            var response =
                    client.post(null, buildInitializeJson(Map.of(TEST_EXT_ID, JsonNodeFactory.instance.objectNode())));
            var sessionId = response.headers().firstValue("MCP-Session-Id").orElseThrow();
            client.sendInitialized(sessionId);

            var listResp = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                    """);
            assertThatJson(listResp.body()).inPath("$.result.tools[0].name").isEqualTo("ext-tool");

            var callResp = client.sendRequest(sessionId, """
                    {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"ext-tool","arguments":{}}}
                    """);
            assertThatJson(callResp.body()).inPath("$.result.content[0].text").isEqualTo("ext-tool-result");
        }
    }

    private static String buildInitializeJson(Map<String, JsonNode> extensions) {
        var capsBuilder = ClientCapabilities.builder();
        if (!extensions.isEmpty()) {
            capsBuilder.extensions(extensions);
        }
        var caps = capsBuilder.build();
        var params = InitializeRequestParams.builder()
                .protocolVersion("2025-11-25")
                .capabilities(caps)
                .clientInfo(new dev.tachyonmcp.protocol.mcp.v2025_11_25.models.Implementation(
                        "1.0", null, null, "test-client", null, null))
                .build();
        var mapper = new tools.jackson.databind.ObjectMapper();
        try {
            var paramsJson = mapper.writeValueAsString(params);
            return """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":%s}
                    """.formatted(paramsJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class TestExtension implements ServerExtension {

        @Override
        public String extensionId() {
            return TEST_EXT_ID;
        }

        @Override
        public Set<String> methods() {
            return Set.of("test/ext-call");
        }

        @Override
        public void bootstrap(Server server) {
            server.registerHandler("test/ext-call", new RpcMethodHandler() {
                @Override
                public String method() {
                    return "test/ext-call";
                }

                @Override
                public Object handle(DispatchContext context, Object params) {
                    return Map.of("status", "ok");
                }
            });
        }
    }

    private static class TestExtensionWithTool implements ServerExtension {

        @Override
        public String extensionId() {
            return TEST_EXT_ID;
        }

        @Override
        public void bootstrap(Server server) {
            server.registerTool(new ToolHandler() {
                private final ToolDescriptor descriptor = ToolDescriptor.builder("ext-tool")
                        .description("Extension-owned tool")
                        .extensionId(TEST_EXT_ID)
                        .build();

                @Override
                public ToolDescriptor descriptor() {
                    return descriptor;
                }

                @Override
                public CompletionStage<ToolResult> handle(InteractionContext context, ToolRequest request) {
                    return CompletableFuture.completedFuture(ToolResult.text("ext-tool-result"));
                }
            });
        }
    }
}
