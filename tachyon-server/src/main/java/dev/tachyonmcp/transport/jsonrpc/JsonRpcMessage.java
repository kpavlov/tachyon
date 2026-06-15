/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.jsonrpc;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public sealed interface JsonRpcMessage {

    @Nullable
    Object id();

    record Request<T>(Object id, String method, @Nullable T params) implements JsonRpcMessage {

        public Request {
            Objects.requireNonNull(method, "method");
        }
    }

    record Response(Object id, String resultJson) implements JsonRpcMessage {

        public Response {
            Objects.requireNonNull(resultJson, "resultJson");
        }
    }

    record Error(
            Object id, int code, String message, @Nullable String dataJson) implements JsonRpcMessage {

        public Error {
            Objects.requireNonNull(message, "message");
        }
    }

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
