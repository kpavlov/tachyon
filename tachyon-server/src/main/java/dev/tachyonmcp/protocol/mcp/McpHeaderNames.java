/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol.mcp;

/**
 * Constants for MCP-related HTTP header names.
 */
public final class McpHeaderNames {

    /**
     * Header carrying the session identifier.
     */
    public static final String MCP_SESSION_ID = "MCP-Session-Id";
    /**
     * Header carrying the protocol version.
     */
    public static final String MCP_PROTOCOL_VERSION = "MCP-Protocol-Version";
    /**
     * SSE {@code Last-Event-ID} header for reconnection.
     */
    public static final String LAST_EVENT_ID = "Last-Event-ID";

    private McpHeaderNames() {
    }
}
