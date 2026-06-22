/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.builder;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.McpServerHandle;
import dev.tachyonmcp.server.extensions.McpExtension;
import dev.tachyonmcp.server.session.SessionLogRouter;
import dev.tachyonmcp.server.session.SessionStore;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;

public final class ExtensionsStep {

    final BuilderState state;

    ExtensionsStep(BuilderState state) {
        this.state = state;
    }

    // === Extensions ===

    public ExtensionsStep extension(McpExtension extension) {
        state.extensions.add(extension);
        return this;
    }

    // === Session (transition) ===

    public SessionStep stateless(boolean stateless) {
        state.stateless = stateless;
        return new SessionStep(state);
    }

    public SessionStep sessionLogRouter(SessionLogRouter router) {
        state.sessionLogRouter = router;
        return new SessionStep(state);
    }

    public SessionStep sessionTtl(Duration sessionTtl) {
        state.sessionTtl = sessionTtl;
        return new SessionStep(state);
    }

    public SessionStep sessionStore(SessionStore sessionStore) {
        state.sessionStore = sessionStore;
        return new SessionStep(state);
    }

    // === Networks (transition) ===

    public NetworksStep endpointPath(String endpointPath) {
        state.endpointPath = endpointPath;
        return new NetworksStep(state);
    }

    public NetworksStep readerIdleTimeout(Duration timeout) {
        state.readerIdleTimeout = timeout;
        return new NetworksStep(state);
    }

    public NetworksStep writerIdleTimeout(Duration timeout) {
        state.writerIdleTimeout = timeout;
        return new NetworksStep(state);
    }

    public NetworksStep host(String host) {
        if (state.addressExplicitlySet) {
            throw new IllegalStateException("Cannot combine host() with address()");
        }
        state.host = host;
        state.hostPortExplicitlySet = true;
        return new NetworksStep(state);
    }

    public NetworksStep port(int port) {
        if (state.addressExplicitlySet) {
            throw new IllegalStateException("Cannot combine port() with address()");
        }
        state.port = port;
        state.hostPortExplicitlySet = true;
        return new NetworksStep(state);
    }

    public NetworksStep address(SocketAddress addr) {
        if (state.hostPortExplicitlySet) {
            throw new IllegalStateException("Cannot combine address() with host()/port()");
        }
        if (addr instanceof InetSocketAddress inet) {
            state.host = inet.getHostString();
            state.port = inet.getPort();
        }
        state.addressExplicitlySet = true;
        return new NetworksStep(state);
    }

    // === Terminal ===

    public McpServer build() {
        return state.build();
    }

    public McpServerHandle bind() {
        return state.bind();
    }
}
