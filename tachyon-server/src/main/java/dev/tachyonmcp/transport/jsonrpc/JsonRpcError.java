/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.jsonrpc;

import org.jspecify.annotations.Nullable;

public record JsonRpcError(
        int code, String message, @Nullable String data) {
    public JsonRpcError(int code, String message) {
        this(code, message, null);
    }
}
