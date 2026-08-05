/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

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
     * Returns a client speaking the given MCP protocol version.
     *
     * @param port the port of the running Tachyon server
     * @param protocolVersion the MCP protocol version
     * @return the matching client
     * @throws IllegalArgumentException for an unsupported protocol version
     */
    public static McpClient forVersion(int port, String protocolVersion) {
        // ponytail: explicit switch; add a case when the next protocol revision ships
        return switch (protocolVersion) {
            case Mcp20260728Client.PROTOCOL_VERSION -> new Mcp20260728Client(port);
            case Mcp20251125Client.PROTOCOL_VERSION -> new Mcp20251125Client(port);
            default -> throw new IllegalArgumentException("Unsupported protocol version: " + protocolVersion);
        };
    }
}
