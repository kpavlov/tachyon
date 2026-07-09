/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

public final class TachyonServer {

    private TachyonServer() {}

    public static ServerBuilder builder() {
        return new ServerBuilder();
    }
}
