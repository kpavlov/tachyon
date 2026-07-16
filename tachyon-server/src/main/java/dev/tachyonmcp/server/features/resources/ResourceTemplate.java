/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

public interface ResourceTemplate {

    ResourceTemplateDescriptor descriptor();

    ResourceTemplateHandler handler();

    static ResourceTemplate of(ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler) {
        return new ResourceTemplateEntry(descriptor, handler);
    }
}
