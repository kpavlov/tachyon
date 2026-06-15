/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import dev.tachyonmcp.server.McpServerHandle;
import dev.tachyonmcp.server.TachyonMcpServer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SharedE2eServer {

    private static final Logger logger = LoggerFactory.getLogger(SharedE2eServer.class);
    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile McpServerHandle handle;

    static synchronized McpServerHandle ensureStarted() {
        if (started.get()) {
            return handle;
        }
        handle = TachyonMcpServer.builder().tool(new EchoToolHandler()).port(0).bind();
        started.set(true);
        Runtime.getRuntime().addShutdownHook(new Thread(handle::close));
        logger.info("Shared E2E server started on port {}", handle.port());
        return handle;
    }
}
