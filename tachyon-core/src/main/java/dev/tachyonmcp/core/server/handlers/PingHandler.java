/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.api.annotations.InternalApi;

@InternalApi
public final class PingHandler extends EmptyResultHandler {

    public PingHandler() {
        super("ping");
    }
}
