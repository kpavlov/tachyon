/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.netty;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ClientCapabilities;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.server.McpDispatcher;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.session.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class ExtensionMethodRoutingTest {

    private McpServer server;
    private McpSession session;
    private McpDispatcher dispatcher;
    private @Nullable McpContext context;

    @BeforeEach
    void setUp() {
        server = TachyonServer.builder().extension(new TestExtension()).build();
        session = server.createSession("sess_routing");
        dispatcher = new McpDispatcher(server, server.executor());
    }

    @Test
    void rejectsExtensionMethodWhenNotNegotiated() {
        session.activate();
        var ctx = new DefaultMcpContext(Protocols.versions().get(0), server);
        ctx.setSession(session);
        var result = (McpDispatcher.DispatchResult.Response) dispatcher
                .dispatchRequestAsync(1, "test/ext-method", null, "sess_routing", null, ctx)
                .join();
        var body = result.responseBody().toString(StandardCharsets.UTF_8);
        assertThat(body).contains("error");
        assertThat(body).contains("-32601");
    }

    @Test
    void dispatchesExtensionMethodWhenNegotiatedAndMetaPresent() throws Exception {
        negotiateExtension();
        session.activate();
        var params = Map.of("_meta", Map.of("com.test/ext", JsonNodeFactory.instance.objectNode()));
        var result = (McpDispatcher.DispatchResult.Response) dispatcher
                .dispatchRequestAsync(1, "test/ext-method", params, "sess_routing", null, context)
                .join();
        var body = result.responseBody().toString(StandardCharsets.UTF_8);
        assertThat(body).contains("result");
    }

    @Test
    void tasksMethodsAreNotGated() {
        assertThat(server.extensionForMethod("tasks/get")).isNull();
        assertThat(server.extensionForMethod("tasks/list")).isNull();
        assertThat(server.extensionForMethod("tasks/result")).isNull();
    }

    private void negotiateExtension() throws Exception {
        var handler = server.getHandler("initialize");
        var caps = ClientCapabilities.builder()
                .extensions(Map.of("com.test/ext", JsonNodeFactory.instance.objectNode()))
                .build();
        var params = InitializeRequestParams.builder()
                .protocolVersion("2025-11-25")
                .capabilities(caps)
                .build();
        var ctx = new DefaultMcpContext(Protocols.versions().get(0), server);
        ctx.setSession(session);
        handler.handle(ctx, params);
        this.context = ctx;
    }

    private static class TestExtension implements McpExtension {

        @Override
        public String extensionId() {
            return "com.test/ext";
        }

        @Override
        public Set<String> methods() {
            return Set.of("test/ext-method");
        }

        @Override
        public boolean requiresMetaEnvelope() {
            return true;
        }

        @Override
        public void bootstrap(McpServer server) {
            server.registerHandler("test/ext-method", new McpMethodHandler() {
                @Override
                public String method() {
                    return "test/ext-method";
                }

                @Override
                public Object handle(McpContext context, Object params) {
                    return Map.of("status", "ok");
                }
            });
        }
    }
}
