/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import dev.tachyonmcp.core.server.TachyonServer;
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
                s -> s.tools().registerAsync(EchoToolHandler.DESCRIPTOR, EchoToolHandler.FN));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        started.set(true);
        logger.info("Shared stateless E2E server started on port {}", server.port());
        return server;
    }
}
