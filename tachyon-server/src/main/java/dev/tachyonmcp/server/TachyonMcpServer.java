/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import dev.tachyonmcp.server.builder.IdentityStep;

public final class TachyonMcpServer {

    private TachyonMcpServer() {}

    public static IdentityStep builder() {
        return new IdentityStep();
    }
}
