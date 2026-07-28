/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.resources;

import dev.tachyonmcp.api.server.ServerFeature;
import dev.tachyonmcp.api.server.domain.UriTemplateValue;
import java.util.Collections;
import java.util.Map;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Request passed to a resource function when a client invokes {@code resources/read}.
 *
 * <p>Carries the concrete resource {@link #uri()} that was read, optionally the
 * {@link #uriTemplate()} that matched, and any URI-template {@link #params()}
 * extracted during matching.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface ResourceRequest extends ServerFeature.Request {

    /**
     * The resource URI being requested.
     */
    String uri();

    /**
     * URI-template variable values extracted during template matching, keyed by
     * variable name. Empty when the request targets a static (non-template) resource.
     */
    @Value.Default
    default Map<String, UriTemplateValue> params() {
        return Collections.emptyMap();
    }

    /**
     * The URI template that matched the request, or {@code null} when the request
     * targets a static (non-template) resource.
     */
    @Nullable
    String uriTemplate();

    @Nullable
    @Override
    Map<String, Object> meta();

    static Builder builder() {
        return DefaultResourceRequest.builder();
    }

    interface Builder {
        Builder uri(String uri);

        Builder params(Map<String, ? extends UriTemplateValue> params);

        Builder uriTemplate(@Nullable String uriTemplate);

        Builder meta(@Nullable Map<String, ?> entries);

        ResourceRequest build();
    }
}
