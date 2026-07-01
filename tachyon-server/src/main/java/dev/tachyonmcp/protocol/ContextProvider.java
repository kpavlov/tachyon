/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol;

import org.jspecify.annotations.Nullable;

/**
 * Provides server-level dependencies to protocol implementations
 * when creating per-channel interaction contexts.
 */
@FunctionalInterface
public interface ContextProvider {
    /** Returns a dependency of the given type, or {@code null} if unavailable. */
    @Nullable
    <T> T provide(Class<T> type);
}
