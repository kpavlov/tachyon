/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.server.domain.TextResourceContents;
import java.util.Collection;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public interface ResourceRegistry {
    default ResourceRegistry add(ResourceDescriptor descriptor) {
        return add(
                descriptor,
                (ctx, request) -> TextResourceContents.of(descriptor.uri(), descriptor.mimeType(), "", null));
    }

    ResourceRegistry add(ResourceDescriptor descriptor, ResourceHandler handler);

    default ResourceRegistry add(Consumer<ResourceDescriptor.Builder> configurer, ResourceHandler handler) {
        final var builder = ResourceDescriptor.builder();
        configurer.accept(builder);
        return add(builder.build(), handler);
    }

    ResourceRegistry remove(String name);

    @Nullable
    ResourceDescriptor get(String name);

    Collection<ResourceDescriptor> getAll();

    ResourceRegistry addTemplate(ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler);

    ResourceRegistry addTemplate(ResourceTemplate template);

    default ResourceRegistry addTemplate(
            Consumer<ResourceTemplateDescriptor.Builder> configurer, ResourceTemplateHandler handler) {
        final var builder = ResourceTemplateDescriptor.builder();
        configurer.accept(builder);
        return addTemplate(builder.build(), handler);
    }

    ResourceRegistry removeTemplate(String name);
}
