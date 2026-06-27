/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.protocol;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ContextProvider {
    @Nullable
    <T> T provide(Class<T> type);
}
