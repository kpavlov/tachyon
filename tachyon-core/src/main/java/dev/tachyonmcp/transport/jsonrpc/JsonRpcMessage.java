/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.transport.jsonrpc;

import dev.tachyonmcp.server.domain.RequestId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A parsed JSON-RPC 2.0 message: request, notification, response, or error. */
public sealed interface JsonRpcMessage {

    /** The request/response ID, or {@code null} for notifications. */
    @Nullable
    RequestId id();

    /**
     * A JSON-RPC request with an ID (expecting a response).
     *
     * @param <T>    the type of the params payload
     * @param id     the request identifier
     * @param method the method name to invoke
     * @param params the method parameters, or {@code null}
     */
    record Request<T>(RequestId id, String method, @Nullable T params) implements JsonRpcMessage {

        public Request {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(method, "method");
        }
    }

    /**
     * A JSON-RPC success response.
     *
     * @param id         the request identifier being responded to, or {@code null}
     * @param resultJson the JSON-encoded result payload
     */
    record Response(@Nullable RequestId id, String resultJson) implements JsonRpcMessage {

        public Response {
            Objects.requireNonNull(resultJson, "resultJson");
        }
    }

    /**
     * A JSON-RPC error response.
     *
     * @param id       the request identifier, or {@code null}
     * @param code     the JSON-RPC error code
     * @param message  a human-readable error message
     * @param dataJson optional JSON-encoded error data, or {@code null}
     */
    record Error(
            @Nullable RequestId id,
            int code,
            String message,
            @Nullable String dataJson) implements JsonRpcMessage {

        public Error {
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * A JSON-RPC notification (no ID, no response expected).
     *
     * @param <T>    the type of the params payload
     * @param method the method name
     * @param params the notification parameters, or {@code null}
     */
    record Notification<T>(String method, @Nullable T params) implements JsonRpcMessage {

        public Notification {
            Objects.requireNonNull(method, "method");
        }

        @Override
        public @Nullable RequestId id() {
            return null;
        }
    }
}
