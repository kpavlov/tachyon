/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.Protocols;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.ClientCapabilities;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.runtime.MutableInteractionContext;
import dev.tachyonmcp.runtime.Session;
import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.extensions.ServerExtension;
import dev.tachyonmcp.server.session.DefaultMcpContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class ExtensionNegotiationTest {

    private Server server;
    private Session session;
    private TestExtension testExtension;

    @BeforeEach
    void setUp() {
        testExtension = new TestExtension();
        server = TachyonServer.builder().extension(testExtension).build();
        session = server.createSession("sess_ext_neg");
    }

    @Test
    void extensionEnabledWhenBothSidesDeclare() throws Exception {
        var handler = server.getHandler("initialize");
        var params = buildInitParams(Map.of("com.test/ext1", JsonNodeFactory.instance.objectNode()));
        var context = context(session, server);
        handler.handle(context, params);

        assertThat(context.isExtensionEnabled("com.test/ext1")).isTrue();
    }

    @Test
    void extensionNotEnabledWhenClientDoesNotDeclare() throws Exception {
        var handler = server.getHandler("initialize");
        var params = buildInitParams(Map.of());
        var context = context(session, server);
        handler.handle(context, params);

        assertThat(context.isExtensionEnabled("com.test/ext1")).isFalse();
    }

    @Test
    void onConnectionInitCalledForNegotiatedExtension() throws Exception {
        var handler = server.getHandler("initialize");
        var params = buildInitParams(Map.of("com.test/ext1", JsonNodeFactory.instance.objectNode()));
        var context = context(session, server);
        handler.handle(context, params);

        assertThat(testExtension.initCalled.get()).isTrue();
    }

    @Test
    void onConnectionInitNotCalledForUnnegotiatedExtension() throws Exception {
        var handler = server.getHandler("initialize");
        var params = buildInitParams(Map.of());
        var context = context(session, server);
        handler.handle(context, params);

        assertThat(testExtension.initCalled.get()).isFalse();
    }

    private static DefaultMcpContext context(Session session, Server server) {
        var ctx = new DefaultMcpContext(Protocols.versions().get(0), server);
        ctx.setSession(session);
        return ctx;
    }

    private static InitializeRequestParams buildInitParams(Map<String, JsonNode> extensions) {
        var capabilities = ClientCapabilities.builder().extensions(extensions).build();
        return InitializeRequestParams.builder()
                .protocolVersion("2025-11-25")
                .capabilities(capabilities)
                .build();
    }

    @Test
    void sessionBackedExtensionStateIsSharedAcrossContexts() {
        var ctxA = context(session, server);
        var ctxB = context(session, server);

        ctxA.enableExtension("test-ext");
        assertThat(ctxA.isExtensionEnabled("test-ext")).isTrue();
        assertThat(ctxB.isExtensionEnabled("test-ext")).isTrue();
    }

    private static class TestExtension implements ServerExtension {

        final AtomicBoolean initCalled = new AtomicBoolean();

        @Override
        public String extensionId() {
            return "com.test/ext1";
        }

        @Override
        public void onConnectionInit(MutableInteractionContext context, Map<String, JsonNode> clientSettings) {
            initCalled.set(true);
        }
    }
}
