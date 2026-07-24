/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.ServerFeature;
import dev.tachyonmcp.server.domain.UriTemplate;

@InternalApi
public record ResourceTemplateEntry(
        ResourceTemplateDescriptor descriptor, ResourceHandler handler, UriTemplate uriTemplate)
        implements ServerFeature<ResourceTemplateDescriptor> {

    /**
     * Creates a resource template entry from its descriptor and handler.
     *
     * @param descriptor the resource template descriptor
     * @param handler    the handler for resource template requests
     * @return the created resource template entry
     */
    public static ResourceTemplateEntry of(ResourceTemplateDescriptor descriptor, ResourceHandler handler) {
        return new ResourceTemplateEntry(descriptor, handler, UriTemplate.create(descriptor.uriTemplate()));
    }
}
