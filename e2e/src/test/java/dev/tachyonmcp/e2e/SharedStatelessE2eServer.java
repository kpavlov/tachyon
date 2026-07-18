/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import dev.tachyonmcp.server.TachyonServer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SharedStatelessE2eServer {

    private static final Logger logger = LoggerFactory.getLogger(SharedStatelessE2eServer.class);
    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile TachyonServer handle;

    static synchronized TachyonServer ensureStarted() {
        if (started.get()) {
            return handle;
        }
        handle = TachyonServer.builder()
                .capabilities(c -> c.tools().logging())
                .tool(EchoToolHandler.create())
                .network(n -> n.port(0))
                .start();
        started.set(true);
        Runtime.getRuntime().addShutdownHook(new Thread(handle::close));
        logger.info("Shared stateless E2E server started on port {}", handle.port());
        return handle;
    }
}
