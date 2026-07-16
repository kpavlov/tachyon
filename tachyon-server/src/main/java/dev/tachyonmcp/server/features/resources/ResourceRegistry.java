/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface ResourceRegistry {

    ResourceRegistry register(ResourceDescriptor descriptor, ResourceHandler handler);

    default ResourceRegistry register(Consumer<ResourceDescriptor.Builder> configurer, ResourceHandler handler) {
        final var builder = ResourceDescriptor.builder();
        configurer.accept(builder);
        return register(builder.build(), handler);
    }

    default ResourceRegistry registerAsync(ResourceDescriptor descriptor, AsyncResourceHandler handler) {
        return register(descriptor, handler);
    }

    default ResourceRegistry registerAsync(
            Consumer<ResourceDescriptor.Builder> descriptor, AsyncResourceHandler handler) {
        final var builder = ResourceDescriptor.builder();
        descriptor.accept(builder);
        return registerAsync(builder.build(), handler);
    }

    boolean unregister(String name);

    Optional<ResourceDescriptor> find(String name);

    List<ResourceDescriptor> descriptors();

    ResourceRegistry registerTemplate(ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler);

    default ResourceRegistry registerTemplate(
            Consumer<ResourceTemplateDescriptor.Builder> configurer, ResourceTemplateHandler handler) {
        final var builder = ResourceTemplateDescriptor.builder();
        configurer.accept(builder);
        return registerTemplate(builder.build(), handler);
    }

    default ResourceRegistry registerTemplateAsync(
            ResourceTemplateDescriptor descriptor, AsyncResourceTemplateHandler handler) {
        return registerTemplate(descriptor, handler);
    }

    default ResourceRegistry registerTemplateAsync(
            Consumer<ResourceTemplateDescriptor.Builder> descriptor, AsyncResourceTemplateHandler handler) {
        final var builder = ResourceTemplateDescriptor.builder();
        descriptor.accept(builder);
        return registerTemplateAsync(builder.build(), handler);
    }

    boolean unregisterTemplate(String name);

    Optional<ResourceTemplateDescriptor> findTemplate(String name);

    List<ResourceTemplateDescriptor> templateDescriptors();
}
