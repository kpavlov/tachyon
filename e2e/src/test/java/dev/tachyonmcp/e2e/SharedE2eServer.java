/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.testkit.McpTestServers;
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
        handle = McpTestServers.startSafely(
                TachyonServer.builder()
                        .capabilities(c -> c.tools().logging())
                        .session(s -> s.enabled(true))
                        .network(n -> n.port(0)),
                s -> s.tools().registerAsync(EchoToolHandler.DESCRIPTOR, EchoToolHandler.FN));
        Runtime.getRuntime().addShutdownHook(new Thread(handle::close));
        started.set(true);
        logger.info("Shared E2E server started on port {}", handle.port());
        return handle;
    }
}
