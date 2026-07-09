/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.runtime;

/**
 * Extension point that can hook into the connection lifecycle and register custom handlers.
 *
 * @param <T> the interaction context type
 */
public interface Extension<T> {

    /** Unique identifier for this extension. */
    String extensionId();

    /** Called after connection initialization is complete. */
    default void onConnectionInit(T context) {}

    /** Called when the connection is being closed. */
    default void onConnectionClose(T context) {}

    /** Called during server shutdown to release resources. */
    default void shutdown() {}
}
