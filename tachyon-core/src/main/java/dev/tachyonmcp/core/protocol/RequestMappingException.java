/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.protocol;

import dev.tachyonmcp.api.server.domain.ServerError;

/** Signals that protocol request parameters could not be mapped to a domain request. */
public final class RequestMappingException extends RuntimeException {

    private final ServerError error;

    public RequestMappingException(ServerError error) {
        super(error.message());
        this.error = error;
    }

    public ServerError error() {
        return error;
    }
}
