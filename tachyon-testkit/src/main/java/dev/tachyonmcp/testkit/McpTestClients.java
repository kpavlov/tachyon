/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import java.net.URI;

/**
 * Factory for {@link McpClient} instances by protocol version.
 */
public final class McpTestClients {

    private McpTestClients() {}

    /**
     * Returns a client for the newest supported MCP protocol version.
     *
     * @param port the port of the running Tachyon server
     * @return the latest protocol client
     */
    public static Mcp20260728Client latest(int port) {
        return new Mcp20260728Client(port);
    }

    /**
     * Returns a client for the newest supported MCP protocol version, against an arbitrary MCP
     * endpoint (local or remote, http or https).
     *
     * @param mcpEndpoint the MCP endpoint URI, e.g. {@code https://staging.example.com/mcp}
     * @return the latest protocol client
     */
    public static Mcp20260728Client latest(URI mcpEndpoint) {
        return new Mcp20260728Client(mcpEndpoint);
    }

    /**
     * Returns a client speaking the given MCP protocol version.
     *
     * @param port the port of the running Tachyon server
     * @param protocolVersion the MCP protocol version
     * @return the matching client
     * @throws IllegalArgumentException for an unsupported protocol version
     */
    public static McpClient forVersion(int port, String protocolVersion) {
        return forVersion(McpClient.localEndpoint(port), protocolVersion);
    }

    /**
     * Returns a client speaking the given MCP protocol version, against an arbitrary MCP endpoint
     * (local or remote, http or https).
     *
     * @param mcpEndpoint the MCP endpoint URI, e.g. {@code https://staging.example.com/mcp}
     * @param protocolVersion the MCP protocol version
     * @return the matching client
     * @throws IllegalArgumentException for an unsupported protocol version
     */
    public static McpClient forVersion(URI mcpEndpoint, String protocolVersion) {
        // ponytail: explicit switch; add a case when the next protocol revision ships
        return switch (protocolVersion) {
            case Mcp20260728Client.PROTOCOL_VERSION -> new Mcp20260728Client(mcpEndpoint);
            case Mcp20251125Client.PROTOCOL_VERSION -> new Mcp20251125Client(mcpEndpoint);
            default -> throw new IllegalArgumentException("Unsupported protocol version: " + protocolVersion);
        };
    }

    /**
     * Returns a fluent builder for an initialized client, against a local port-0-style server.
     *
     * @param port the port of the running Tachyon server
     * @return a new builder
     */
    public static McpTestClientBuilder builder(int port) {
        return builder(McpClient.localEndpoint(port));
    }

    /**
     * Returns a fluent builder for an initialized client, against an arbitrary MCP endpoint (local
     * or remote, http or https).
     *
     * @param mcpEndpoint the MCP endpoint URI, e.g. {@code https://staging.example.com/mcp}
     * @return a new builder
     */
    public static McpTestClientBuilder builder(URI mcpEndpoint) {
        return new McpTestClientBuilder(mcpEndpoint);
    }
}
