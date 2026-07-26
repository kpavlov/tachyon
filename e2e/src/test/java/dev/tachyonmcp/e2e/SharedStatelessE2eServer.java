/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import dev.tachyonmcp.server.TachyonServer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SharedStatelessE2eServer {

    private static final Logger logger = LoggerFactory.getLogger(SharedStatelessE2eServer.class);
    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile TachyonServer server;

    static synchronized TachyonServer ensureStarted() {
        if (started.get()) {
            return server;
        }
        server = TestServers.startSafely(
                TachyonServer.builder().capabilities(c -> c.tools().logging()).network(n -> n.port(0)),
                s -> s.tools().register(EchoToolHandler.create()));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        started.set(true);
        logger.info("Shared stateless E2E server started on port {}", server.port());
        return server;
    }
}
