/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import dev.tachyonmcp.core.server.ServerBuilder;
import dev.tachyonmcp.core.server.TachyonServer;
import java.util.function.Consumer;

/**
 * In-process server lifecycle helpers for tests: build, register handlers, start on an ephemeral
 * (port 0) port, and release the transport on any failure so it doesn't leak.
 */
public final class McpTestServers {

    private McpTestServers() {}

    /**
     * Builds a port-0 server, applies {@code configurer}, registers handlers via {@code registrar},
     * and starts it. The returned server reports its bound port via {@link TachyonServer#port()}.
     *
     * @param configurer builder configuration, applied before the port-0 default
     * @param registrar registers handlers on the built (not yet started) server
     * @return the started server
     */
    public static TachyonServer start(Consumer<ServerBuilder> configurer, Consumer<TachyonServer> registrar) {
        var builder = TachyonServer.builder().port(0);
        configurer.accept(builder);
        return startSafely(builder, registrar);
    }

    /**
     * Builds, registers, and starts the given server, closing it on failure so it doesn't leak.
     *
     * @param builder the configured builder (may use port 0)
     * @param registrar registers handlers on the freshly built (not yet started) server
     * @return the started server
     */
    public static TachyonServer startSafely(ServerBuilder builder, Consumer<TachyonServer> registrar) {
        var candidate = builder.build();
        try {
            registrar.accept(candidate);
            candidate.start();
            return candidate;
        } catch (RuntimeException | Error failure) {
            try {
                candidate.close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
