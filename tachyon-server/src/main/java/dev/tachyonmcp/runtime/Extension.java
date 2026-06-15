/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.runtime;

public interface Extension<T> {

    String extensionId();

    default void onConnectionInit(T context) {}

    default void onConnectionClose(T context) {}

    default void shutdown() {}
}
