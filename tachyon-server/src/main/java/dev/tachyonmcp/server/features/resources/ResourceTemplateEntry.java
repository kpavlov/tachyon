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
        implements ServerFeature, ResourceTemplate {

    @Override
    public String name() {
        return descriptor.name();
    }

    List<String> paramNames() {
        return descriptor.paramNames();
    }

    Pattern compiledPattern() {
        return descriptor.compiledPattern();
    }

    public static ResourceTemplateEntry of(ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler) {
        return new ResourceTemplateEntry(descriptor, handler);
    }

    public static ResourceTemplateEntry of(ResourceTemplate resourceTemplate) {
        if (resourceTemplate instanceof ResourceTemplateEntry entry) {
            return entry;
        }
        return new ResourceTemplateEntry(resourceTemplate.descriptor(), resourceTemplate.handler());
    }
}
