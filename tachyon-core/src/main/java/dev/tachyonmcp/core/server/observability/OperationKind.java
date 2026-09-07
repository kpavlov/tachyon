/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.observability;

import dev.tachyonmcp.api.annotations.InternalApi;

/** The shape of an inbound MCP operation being observed. */
@InternalApi
public enum OperationKind {
    /** An ordinary JSON-RPC request (tools/resources/prompts/etc.). */
    REQUEST,
    /** A JSON-RPC notification (no response expected). */
    NOTIFICATION,
    /** The {@code initialize} handshake request. */
    INITIALIZE
}
