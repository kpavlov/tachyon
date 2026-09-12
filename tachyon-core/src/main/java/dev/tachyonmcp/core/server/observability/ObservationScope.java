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
 *
 * <p>Scopes from multiple registered listeners nest: a listener registered later is opened inside
 * one registered earlier, and the dispatcher closes them innermost-first. An implementation may
 * therefore assume {@link #close()} runs while its own context is the current one, and restore
 * whatever context it displaced.
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

    /**
     * Re-attaches this scope's context onto the current thread for one bounded phase of work,
     * returning the handle to close it. May be called more than once over an operation's lifetime —
     * once per phase that runs after an executor hop — but never concurrently for the same
     * operation.
     */
    ObservationScope reattach();
}
