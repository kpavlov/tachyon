/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.domain.HasMeta;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Request passed to a resource handler.
 *
 * @param uri requested resource URI
 * @param params immutable URI-template parameters; empty for a static resource
 * @param uriTemplate original URI template; null for a static resource
 * @param meta optional client request metadata ({@code _meta})
 */
public record ResourceRequest(
        String uri,
        Map<String, UriTemplateValue> params,
        @Nullable String uriTemplate,
        @Nullable Map<String, JsonNode> meta)
        implements HasMeta {

    public ResourceRequest {
        params = Map.copyOf(params);
        meta = meta == null ? null : Map.copyOf(meta);
    }
}
