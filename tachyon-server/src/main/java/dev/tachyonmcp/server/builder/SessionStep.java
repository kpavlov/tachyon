/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.builder;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.McpServerHandle;
import dev.tachyonmcp.server.session.SessionLogRouter;
import dev.tachyonmcp.server.session.SessionStore;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;

/**
 * Step: configure session behaviour (log router, TTL).
 * Call feature methods to go back, or networks methods to transition onward.
 */
public final class SessionStep {

    final BuilderState state;

    SessionStep(BuilderState state) {
        this.state = state;
    }

    // === Session ===

    public SessionStep stateless(boolean stateless) {
        state.stateless = stateless;
        return this;
    }

    public SessionStep sessionLogRouter(SessionLogRouter router) {
        state.sessionLogRouter = router;
        return this;
    }

    public SessionStep sessionTtl(Duration sessionTtl) {
        state.sessionTtl = sessionTtl;
        return this;
    }

    public SessionStep sessionStore(SessionStore sessionStore) {
        state.sessionStore = sessionStore;
        return this;
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
