/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.runtime;

import java.util.concurrent.CompletableFuture;

/**
 * Requests additional information from the user via the client ({@code elicitation/create}).
 *
 * @see ClientContext#elicitation()
 */
@FunctionalInterface
public interface ElicitationService {

    /**
     * Sends an elicitation request to the client and returns a future completed with the user's response.
     *
     * @param request the elicitation request
     * @return a future that completes with the client's response
     */
    CompletableFuture<ElicitationResult> create(ElicitationRequest request);
}
