/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A request from the dispatcher to read a resource by URI.
 *
 * <p>Carries the target {@code uri} and optional request-level {@code meta} that may be
 * forwarded to the resource handler for additional context.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ReadResourceRequest extends HasMeta {

    String uri();

    @Nullable
    Map<String, JsonNode> meta();

    static DefaultReadResourceRequest.Builder builder() {
        return DefaultReadResourceRequest.builder();
    }

    static ReadResourceRequest of(String uri, @Nullable Map<String, JsonNode> meta) {
        return DefaultReadResourceRequest.of(uri, meta);
    }
}
