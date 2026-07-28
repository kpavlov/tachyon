/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e;

import dev.tachyonmcp.core.server.ServerBuilder;
import dev.tachyonmcp.core.server.TachyonServer;
import java.util.function.Consumer;

/** Builds, registers, and starts a server, closing it on failure so it doesn't leak. */
final class TestServers {

    private TestServers() {}

    static TachyonServer startSafely(ServerBuilder builder, Consumer<TachyonServer> registrar) {
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
