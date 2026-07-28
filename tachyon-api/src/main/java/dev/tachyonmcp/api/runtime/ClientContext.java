/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.runtime;

/**
 * Typed access to the client-facing collaboration channels a handler may invoke:
 * elicitation and sampling round-trips.
 *
 * @see InteractionContext#client()
 */
public interface ClientContext {

    /**
     * Returns the elicitation service for requesting additional information from the user.
     *
     * @return the elicitation service
     */
    ElicitationService elicitation();

    /**
     * Returns the sampling service for requesting an LLM completion from the client.
     *
     * @return the sampling service
     */
    SamplingService sampling();
}
