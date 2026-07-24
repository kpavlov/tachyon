/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.jsonrpc;

import org.jspecify.annotations.Nullable;

/**
 * A JSON-RPC error object, produced by a protocol version's {@code ProtocolResponseMapper}.
 *
 * @param code       the error code
 * @param message    a short description of the error
 * @param data       optional additional error data
 * @param httpStatus the HTTP status this protocol version ties to this error (200 by default —
 *                   the JSON-RPC-over-HTTP convention where the transport succeeds even though
 *                   the RPC failed; protocol versions that tie specific errors to HTTP status
 *                   codes, e.g. MCP 2026-07-28, set this per error kind)
 */
public record JsonRpcError(
        int code, String message, @Nullable String data, int httpStatus) {
    /** Creates an error with no additional data, HTTP status 200. */
    public JsonRpcError(int code, String message) {
        this(code, message, null, 200);
    }

    /** Creates an error with additional data, HTTP status 200. */
    public JsonRpcError(int code, String message, @Nullable String data) {
        this(code, message, data, 200);
    }
}
