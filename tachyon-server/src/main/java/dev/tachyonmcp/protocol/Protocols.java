/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.protocol;

import dev.tachyonmcp.annotations.InternalApi;
import io.netty.handler.codec.http.HttpRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry of {@link Protocol} implementations discovered via {@link ServiceLoader}.
 */
@InternalApi
public final class Protocols {

    private Protocols() {}

    private static final List<Protocol> PROTOCOLS;

    static {
        PROTOCOLS = ServiceLoader.load(Protocol.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        if (PROTOCOLS.isEmpty()) {
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
        return PROTOCOLS.stream()
                .filter(pv -> pv.matches(request))
                .max(Comparator.comparing(Protocol::versionString).thenComparingInt(Protocol::priority));
    }

    /**
     * All registered protocol implementations.
     */
    public static List<Protocol> list() {
        return PROTOCOLS;
    }
}
