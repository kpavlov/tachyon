/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol;

import io.netty.handler.codec.http.HttpRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry of {@link Protocol} implementations discovered via {@link ServiceLoader}.
 */
public final class Protocols {

    private Protocols() {}

    static final List<Protocol> VERSIONS;

    static {
        VERSIONS = ServiceLoader.load(Protocol.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        if (VERSIONS.isEmpty()) {
            throw new IllegalStateException("No Protocol implementations found.");
        }
    }

    /**
     * Resolves the best {@link Protocol} for the given HTTP request.
     *
     * <p>Filters all registered versions where {@link Protocol#matches(HttpRequest)}
     * returns {@code true} (the "intersecting set" of what client and server both support),
     * then picks the one with the highest {@link Protocol#versionString()} — the
     * newest/highest version in the intersection.
     */
    public static Optional<Protocol> resolve(HttpRequest request) {
        return VERSIONS.stream()
                .filter(pv -> pv.matches(request))
                .max(Comparator.comparing(Protocol::versionString).thenComparingInt(Protocol::priority));
    }

    /**
     * All registered protocol version implementations.
     */
    public static List<Protocol> versions() {
        return VERSIONS;
    }
}
