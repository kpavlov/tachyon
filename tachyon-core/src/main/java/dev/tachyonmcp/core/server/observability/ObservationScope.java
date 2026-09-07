/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.observability;

import dev.tachyonmcp.api.annotations.InternalApi;

/**
 * A handle a listener can use to make its recorded context (e.g. an OpenTelemetry span) current
 * on whichever thread is executing dispatch work right now.
 *
 * <p>The dispatcher closes a scope on the same thread it opened or reattached it on, and never
 * keeps one open across an incomplete {@code CompletionStage} — only across the synchronous
 * dispatch work that runs while attached.
 */
@InternalApi
public interface ObservationScope {

    /** A shared no-op scope for listeners whose {@code start}/{@code reattach} call failed or is absent. */
    ObservationScope NOOP = new ObservationScope() {
        @Override
        public void close() {}

        @Override
        public ObservationScope reattach() {
            return this;
        }
    };

    /** Detaches this scope's context from the current thread. */
    void close();

    /** Re-attaches this scope's context onto the current thread, returning the handle to close it. */
    ObservationScope reattach();
}
