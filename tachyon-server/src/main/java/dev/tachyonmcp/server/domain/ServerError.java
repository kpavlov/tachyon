/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/** A protocol-neutral server error returned by server code. */
public record ServerError(
        Kind kind, String message, @Nullable Object data) {

    public enum Kind {
        PARSE_ERROR,
        INVALID_REQUEST,
        METHOD_NOT_FOUND,
        INVALID_PARAMS,
        INTERNAL_ERROR,
        RESOURCE_NOT_FOUND,
        HEADER_MISMATCH,
        MISSING_REQUIRED_CLIENT_CAPABILITY,
        UNSUPPORTED_PROTOCOL_VERSION
    }

    public ServerError(Kind kind, String message) {
        this(kind, message, null);
    }
}
