/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.conformance;

import dev.tachyonmcp.server.Server;
import dev.tachyonmcp.server.TachyonServer;

class DefaultConformanceServer extends AbstractConformanceServer {

    @Override
    protected Server createServer(boolean isStateful) {
        return TachyonServer.builder()
                .session(s -> s.enabled(isStateful))
                .network(n -> n.host("localhost"))
                .build();
    }
}
