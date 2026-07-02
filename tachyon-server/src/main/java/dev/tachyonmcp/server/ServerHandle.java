/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

import java.io.Closeable;
import java.io.IOException;

/**
 * Handle returned by the server builder's {@code start()} / {@code startAsync()} method.
 * Combines the logical {@link Server} and the bound transport.
 * Call {@link #close()} to shut down both.
 */
public final class ServerHandle implements Closeable {

    private final Server server;
    private final int port;
    private final Closeable transport;

    public ServerHandle(Server server, int port, Closeable transport) {
        this.server = server;
        this.port = port;
        this.transport = transport;
    }

    /** Returns the port the server is bound to. */
    public int port() {
        return port;
    }

    /** Returns the underlying {@link Server}. */
    public Server server() {
        return server;
    }

    @Override
    public void close() {
        try {
            transport.close();
        } catch (IOException e) {
            // transport close errors are non-fatal; server close follows
        }
        server.close();
    }
}
