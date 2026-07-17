/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.TachyonServer;
import dev.tachyonmcp.server.internal.ServerEngine;

class DefaultConformanceServer extends AbstractConformanceServer {

    @Override
    protected ServerEngine createServer(boolean isStateful) {
        return (ServerEngine) TachyonServer.builder()
                .capabilities(c -> c.logging())
                .session(s -> s.enabled(isStateful))
                .network(n -> n.host("localhost"))
                .build();
    }
}
