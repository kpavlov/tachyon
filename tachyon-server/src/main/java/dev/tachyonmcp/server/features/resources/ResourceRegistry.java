/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.resources;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface ResourceRegistry {

    /**
     * Registers a resource descriptor with its handler.
     *
     * @param descriptor the resource descriptor to register
     * @param handler    the handler for the resource
     * @return this registry
     */
    ResourceRegistry register(ResourceDescriptor descriptor, ResourceHandler handler);

    /**
     * Registers a resource configured through a descriptor builder.
     *
     * @param configurer configures the resource descriptor
     * @param handler    handles requests for the registered resource
     * @return this resource registry
     */
    default ResourceRegistry register(Consumer<ResourceDescriptor.Builder> configurer, ResourceHandler handler) {
        final var builder = ResourceDescriptor.builder();
        configurer.accept(builder);
        return register(builder.build(), handler);
    }

    /**
     * Registers a resource descriptor with an asynchronous handler.
     *
     * @return this resource registry for method chaining
     */
    default ResourceRegistry registerAsync(ResourceDescriptor descriptor, AsyncResourceHandler handler) {
        return register(descriptor, handler);
    }

    /**
     * Registers an asynchronous resource handler using a descriptor configured through a builder.
     *
     * @param descriptor a consumer that configures the resource descriptor builder
     * @param handler    the asynchronous handler for the resource
     * @return this resource registry
     */
    default ResourceRegistry registerAsync(
            Consumer<ResourceDescriptor.Builder> descriptor, AsyncResourceHandler handler) {
        final var builder = ResourceDescriptor.builder();
        descriptor.accept(builder);
        return registerAsync(builder.build(), handler);
    }

    /**
     * Removes the registered resource with the specified name.
     *
     * @param name the name of the resource to remove
     * @return {@code true} if a resource was removed, {@code false} otherwise
     */
    boolean unregister(String name);

    /**
     * Finds a registered resource descriptor by name.
     *
     * @param name the resource name
     * @return the matching descriptor, or an empty {@code Optional} if no resource is registered with that name
     */
    Optional<ResourceDescriptor> find(String name);

    /**
     * Lists the descriptors for all registered resources.
     *
     * @return the registered resource descriptors
     */
    List<ResourceDescriptor> descriptors();

    /**
     * Registers a resource template descriptor with its handler.
     *
     * @param descriptor the resource template descriptor to register
     * @param handler the handler for the registered resource template
     * @return this registry
     */
    ResourceRegistry registerTemplate(ResourceTemplateDescriptor descriptor, ResourceTemplateHandler handler);

    /**
     * Registers a resource template configured through its builder.
     *
     * @param configurer configures the resource template descriptor
     * @param handler    handles requests for the registered resource template
     * @return this resource registry
     */
    default ResourceRegistry registerTemplate(
            Consumer<ResourceTemplateDescriptor.Builder> configurer, ResourceTemplateHandler handler) {
        final var builder = ResourceTemplateDescriptor.builder();
        configurer.accept(builder);
        return registerTemplate(builder.build(), handler);
    }

    /**
     * Registers a resource template with an asynchronous handler.
     *
     * @param descriptor the resource template descriptor to register
     * @param handler    the asynchronous handler for the resource template
     * @return this resource registry
     */
    default ResourceRegistry registerTemplateAsync(
            ResourceTemplateDescriptor descriptor, AsyncResourceTemplateHandler handler) {
        return registerTemplate(descriptor, handler);
    }

    /**
     * Registers a resource template configured through its builder.
     *
     * @param descriptor a consumer that configures the resource template builder
     * @param handler    the asynchronous handler for the registered resource template
     * @return this resource registry
     */
    default ResourceRegistry registerTemplateAsync(
            Consumer<ResourceTemplateDescriptor.Builder> descriptor, AsyncResourceTemplateHandler handler) {
        final var builder = ResourceTemplateDescriptor.builder();
        descriptor.accept(builder);
        return registerTemplateAsync(builder.build(), handler);
    }

    /**
     * Removes the registered resource template with the specified name.
     *
     * @param name the name of the resource template to remove
     * @return {@code true} if the resource template was removed, {@code false} otherwise
     */
    boolean unregisterTemplate(String name);

    /**
     * Finds a registered resource template descriptor by name.
     *
     * @param name the name of the resource template
     * @return the matching descriptor, if registered
     */
    Optional<ResourceTemplateDescriptor> findTemplate(String name);

    /**
     * Lists all registered resource template descriptors.
     *
     * @return the registered resource template descriptors
     */
    List<ResourceTemplateDescriptor> templateDescriptors();
}
