/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.builder;

import dev.tachyonmcp.server.McpServer;
import dev.tachyonmcp.server.McpServerHandle;
import io.netty.channel.ChannelPipeline;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Final step: configure networking (port, and in the future host, Netty settings, timeouts).
 * Call session methods to go back, or invoke a terminal method.
 */
public final class NetworksStep {

    final BuilderState state;

    NetworksStep(BuilderState state) {
        this.state = state;
    }

    // === Networks ===

    /** Sets the HTTP path at which the MCP endpoint is served. Default: {@code /mcp}. */
    public NetworksStep endpointPath(String endpointPath) {
        state.endpointPath = endpointPath;
        return this;
    }

    /** Closes idle connections that have not received data for {@code timeout}. Default: 60 s. */
    public NetworksStep readerIdleTimeout(Duration timeout) {
        state.readerIdleTimeout = timeout;
        return this;
    }

    /** Closes idle connections that have not sent data for {@code timeout}. Default: 5 min. */
    public NetworksStep writerIdleTimeout(Duration timeout) {
        state.writerIdleTimeout = timeout;
        return this;
    }

    /**
     * Binds to the given host name or IP address. Default: {@code 127.0.0.1}.
     * Cannot be combined with {@link #address(SocketAddress)}.
     */
    public NetworksStep host(String host) {
        if (state.addressExplicitlySet) {
            throw new IllegalStateException("Cannot combine host() with address()");
        }
        state.host = host;
        state.hostPortExplicitlySet = true;
        return this;
    }

    /**
     * Binds to the given TCP port. Use {@code 0} for an OS-assigned ephemeral port.
     * Cannot be combined with {@link #address(SocketAddress)}.
     */
    public NetworksStep port(int port) {
        if (state.addressExplicitlySet) {
            throw new IllegalStateException("Cannot combine port() with address()");
        }
        state.port = port;
        state.hostPortExplicitlySet = true;
        return this;
    }

    /**
     * Binds to the given socket address. Convenience alternative to {@link #host(String)} +
     * {@link #port(int)}; cannot be combined with either.
     */
    public NetworksStep address(SocketAddress addr) {
        if (state.hostPortExplicitlySet) {
            throw new IllegalStateException("Cannot combine address() with host()/port()");
        }
        if (addr instanceof InetSocketAddress inet) {
            state.host = inet.getHostString();
            state.port = inet.getPort();
        }
        state.addressExplicitlySet = true;
        return this;
    }

    /**
     * Restricts browser access to the listed origins (e.g. {@code "http://localhost:3000"}).
     * Requests with an {@code Origin} header not in this list are rejected with {@code 403}.
     * Requests without an {@code Origin} header (CLI / non-browser clients) are always allowed.
     * Default: {@code http://localhost} and {@code http://127.0.0.1} (any port).
     */
    public NetworksStep allowedOrigins(String... origins) {
        state.allowedOrigins = List.of(origins);
        return this;
    }

    /**
     * When {@code true}, allows requests with {@code Origin: null}
     * (e.g. pages loaded from the filesystem). Default: {@code false}.
     */
    public NetworksStep allowNullOrigin(boolean allow) {
        state.allowNullOrigin = allow;
        return this;
    }

    /**
     * When {@code true}, adds the {@code Access-Control-Allow-Private-Network} response header,
     * enabling Chrome's Private Network Access requests from public pages. Default: {@code false}.
     */
    public NetworksStep allowPrivateNetworks(boolean allow) {
        state.allowPrivateNetworks = allow;
        return this;
    }

    /**
     * Declares additional request headers that browsers are permitted to send.
     * Standard MCP headers are always included; use this for custom or forwarded headers.
     */
    public NetworksStep allowedHeaders(String... headers) {
        state.allowedHeaders = List.of(headers);
        return this;
    }

    /**
     * Provides a hook to add or replace Netty pipeline handlers after the standard MCP
     * handler chain has been assembled. Use sparingly; prefer higher-level configuration.
     */
    public NetworksStep pipelineCustomizer(@Nullable Consumer<ChannelPipeline> customizer) {
        state.pipelineCustomizer = customizer;
        return this;
    }

    // === Terminal ===

    /** Builds the {@link McpServer} without starting a transport. */
    public McpServer build() {
        return state.build();
    }

    /** Builds and immediately starts the server, returning a handle for shutdown. */
    public McpServerHandle bind() {
        return state.bind();
    }
}
