/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.ServerFeature;
import dev.tachyonmcp.server.domain.UriTemplate;

@InternalApi
public record ResourceTemplateEntry(
        ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler, UriTemplate uriTemplate)
        implements ServerFeature<ResourceTemplateDescriptor> {

    public static ResourceTemplateEntry of(ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler) {
        return new ResourceTemplateEntry(descriptor, handler, UriTemplate.create(descriptor.uriTemplate()));
    }
}
