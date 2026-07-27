/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.protocol.api.annotations.InternalApi;
import dev.tachyonmcp.protocol.api.server.ServerFeature;
import dev.tachyonmcp.protocol.api.server.domain.UriTemplate;
import dev.tachyonmcp.protocol.api.server.features.resources.ResourceHandler;
import dev.tachyonmcp.protocol.api.server.features.resources.ResourceTemplateDescriptor;

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
