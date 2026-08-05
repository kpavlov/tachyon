/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

/**
 * {@link McpClient} for MCP protocol version 2025-11-25 (session-based, {@code initialize}
 * handshake).
 */
public final class Mcp20251125Client extends McpClient {

    /** The MCP protocol version this client speaks. */
    public static final String PROTOCOL_VERSION = "2025-11-25";

    /**
     * Creates a client for the given server port.
     *
     * @param port the port of the running Tachyon server
     */
    public Mcp20251125Client(int port) {
        super(port);
    }

    @Override
    protected String protocolVersion() {
        return PROTOCOL_VERSION;
    }
}
