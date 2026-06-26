/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

/**
 * @deprecated Use {@link TachyonServer instead}
 */
@Deprecated
public final class TachyonMcpServer {

    private TachyonMcpServer() {}

    public static ServerBuilder builder() {
        return TachyonServer.builder();
    }
}
