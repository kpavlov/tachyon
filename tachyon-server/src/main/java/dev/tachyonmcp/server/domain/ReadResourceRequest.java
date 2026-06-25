/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A request from the dispatcher to read a resource by URI.
 *
 * <p>Carries the target {@code uri} and optional request-level {@code meta} that may be
 * forwarded to the resource handler for additional context.
 */
public interface ReadResourceRequest extends HasMeta {

    String uri();

    static ReadResourceRequest of(String uri, @Nullable Map<String, JsonNode> meta) {
        return new DefaultReadResourceRequest(uri, meta);
    }
}
