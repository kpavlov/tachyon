/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.jsonrpc;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A parsed JSON-RPC 2.0 message: request, notification, response, or error. */
public sealed interface JsonRpcMessage {

    /** The request/response ID, or {@code null} for notifications. */
    @Nullable
    Object id();

    /** A JSON-RPC request with an ID (expecting a response). */
    record Request<T>(Object id, String method, @Nullable T params) implements JsonRpcMessage {

        public Request {
            Objects.requireNonNull(method, "method");
        }
    }

    /** A JSON-RPC success response. */
    record Response(Object id, String resultJson) implements JsonRpcMessage {

        public Response {
            Objects.requireNonNull(resultJson, "resultJson");
        }
    }

    /** A JSON-RPC error response. */
    record Error(
            Object id, int code, String message, @Nullable String dataJson) implements JsonRpcMessage {

        public Error {
            Objects.requireNonNull(message, "message");
        }
    }

    /** A JSON-RPC notification (no ID, no response expected). */
    record Notification<T>(String method, @Nullable T params) implements JsonRpcMessage {

        public Notification {
            Objects.requireNonNull(method, "method");
        }

        @Override
        public @Nullable Object id() {
            return null;
        }
    }
}
