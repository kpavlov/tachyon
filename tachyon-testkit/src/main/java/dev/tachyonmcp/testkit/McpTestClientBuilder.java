/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for an initialized {@link McpClient}: pick a protocol version, {@link #build()},
 * and get back a client that has already completed the {@code initialize} handshake (where the
 * chosen protocol version has one — protocol versions without a handshake, e.g. 2026-07-28,
 * propagate {@link UnsupportedOperationException} from {@code build()}).
 *
 * <p>Obtained via {@link McpTestClients#builder(int)} (local port-0 server) or {@link
 * McpTestClients#builder(URI)} (arbitrary endpoint, local or remote).
 *
 * <pre>{@code
 * try (var client = McpTestClients.builder(port).protocolVersion("2025-11-25").build()) {
 *     var ping = client.sendRpc("""
 *             {"jsonrpc":"2.0","id":1,"method":"ping"}
 *             """);
 * }
 * }</pre>
 */
public final class McpTestClientBuilder {

    private final URI mcpEndpoint;
    private @Nullable String protocolVersion;

    McpTestClientBuilder(URI mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
    }

    /**
     * Sets the MCP protocol version the built client will speak.
     *
     * @param protocolVersion the MCP protocol version, e.g. {@code "2025-11-25"}
     * @return {@code this}
     */
    public McpTestClientBuilder protocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
        return this;
    }

    /**
     * Resolves the client for the configured protocol version and completes its {@code
     * initialize} handshake.
     *
     * @return an initialized client
     * @throws IllegalStateException if {@link #protocolVersion(String)} was never called
     * @throws IllegalArgumentException for an unsupported protocol version
     * @throws Exception if the {@code initialize} handshake fails
     */
    public McpClient build() throws Exception {
        if (protocolVersion == null) {
            throw new IllegalStateException("protocolVersion() must be set before build()");
        }
        var client = McpTestClients.forVersion(mcpEndpoint, protocolVersion);
        client.initialize();
        return client;
    }
}
