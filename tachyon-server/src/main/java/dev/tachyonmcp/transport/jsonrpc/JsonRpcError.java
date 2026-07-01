/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.jsonrpc;

import org.jspecify.annotations.Nullable;

/**
 * A JSON-RPC error object.
 *
 * @param code    the error code
 * @param message a short description of the error
 * @param data    optional additional error data
 */
public record JsonRpcError(
        int code, String message, @Nullable String data) {
    /** Creates an error with no additional data. */
    public JsonRpcError(int code, String message) {
        this(code, message, null);
    }
}
