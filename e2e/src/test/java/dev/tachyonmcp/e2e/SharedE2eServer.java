/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import dev.tachyonmcp.server.TachyonServer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SharedE2eServer {

    private static final Logger logger = LoggerFactory.getLogger(SharedE2eServer.class);
    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile TachyonServer handle;

    static synchronized TachyonServer ensureStarted() {
        if (started.get()) {
            return handle;
        }
        handle = TachyonServer.builder()
                .capabilities(c -> c.tools().logging())
                .session(s -> s.enabled(true))
                .network(n -> n.port(0))
                .build();
        handle.tools().register(EchoToolHandler.create());
        handle.start();
        started.set(true);
        Runtime.getRuntime().addShutdownHook(new Thread(handle::close));
        logger.info("Shared E2E server started on port {}", handle.port());
        return handle;
    }
}
