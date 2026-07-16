/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.server.ServerFeature;
import java.util.List;
import java.util.regex.Pattern;

@InternalApi
public record ResourceTemplateEntry(ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler)
        implements ServerFeature<ResourceTemplateDescriptor> {

    List<String> paramNames() {
        return descriptor.paramNames();
    }

    Pattern compiledPattern() {
        return descriptor.compiledPattern();
    }

    public static ResourceTemplateEntry of(ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler) {
        return new ResourceTemplateEntry(descriptor, handler);
    }
}
