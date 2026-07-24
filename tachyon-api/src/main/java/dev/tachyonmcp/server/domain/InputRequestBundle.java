/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Shared payload for an MCP {@code input_required} outcome: the pending input requests keyed by
 * name, plus optional opaque continuation state. Values must be non-null; the map is defensively
 * copied and made immutable.
 */
public record InputRequestBundle(
        Map<String, ? extends InputRequest> inputRequests,
        @Nullable String requestState) {

    public InputRequestBundle {
        Objects.requireNonNull(inputRequests, "inputRequests");
        inputRequests = Map.copyOf(inputRequests);
    }
}
