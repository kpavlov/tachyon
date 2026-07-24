/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.resources;

import dev.tachyonmcp.annotations.ExperimentalApi;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Façade interface for MCP resources
 */
public interface Resources {

    /**
     * Registers a resource descriptor with its handler.
     *
     * <p>Registering a resource whose name already exists replaces it. A URI is unique across
     * resources: registering a URI already owned by a different name is rejected.
     *
     * @param descriptor the resource descriptor to register
     * @param handler    the handler for the resource
     * @return this registry
     * @throws IllegalArgumentException if the URI is already registered under a different name
     */
    Resources register(ResourceDescriptor descriptor, ResourceHandler handler);

    /**
     * Registers a resource configured through a descriptor builder.
     *
     * @param configurer configures the resource descriptor
     * @param handler    handles requests for the registered resource
     * @return this resource registry
     */
    default Resources register(Consumer<ResourceDescriptor.Builder> configurer, ResourceHandler handler) {
        final var builder = ResourceDescriptor.builder();
        configurer.accept(builder);
        return register(builder.build(), handler);
    }

    /**
     * Registers a resource descriptor with an asynchronous handler. The handler and the blocking
     * wait for its result run on a server-executor virtual thread.
     *
     * @return this resource registry for method chaining
     */
    default Resources registerAsync(ResourceDescriptor descriptor, AsyncResourceHandler handler) {
        return register(descriptor, handler);
    }

    /**
     * Registers an asynchronous resource handler using a descriptor configured through a builder.
     *
     * @param descriptor a consumer that configures the resource descriptor builder
     * @param handler    the asynchronous handler for the resource
     * @return this resource registry
     */
    default Resources registerAsync(Consumer<ResourceDescriptor.Builder> descriptor, AsyncResourceHandler handler) {
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
     * Removes the registered resource with the specified uri.
     *
     * @param uri resource URI
     * @return true if resource was unregistered
     */
    @ExperimentalApi
    boolean unregisterByUri(String uri);

    /**
     * Finds a registered resource descriptor by name.
     *
     * @param name the resource name
     * @return the matching descriptor, or an empty {@code Optional} if no resource is registered with that name
     */
    Optional<ResourceDescriptor> find(String name);

    /**
     * Finds a registered resource descriptor by URI.
     *
     * @param uri the resource URI
     * @return the matching descriptor, or an empty {@code Optional} if no resource is registered with that uri
     */
    @ExperimentalApi
    Optional<ResourceDescriptor> findByUri(String uri);

    /**
     * Lists the descriptors for all registered resources.
     *
     * @return the registered resource descriptors
     */
    List<ResourceDescriptor> descriptors();

    /**
     * Notifies every session subscribed to the given resource URI that the resource has changed,
     * emitting a {@code notifications/resources/updated} notification to each.
     *
     * <p>Has no effect when no session is subscribed to the URI.
     *
     * @param uri the URI of the resource that changed
     */
    void notifyResourceUpdated(String uri);

    /**
     * Registers a resource template descriptor with its handler.
     *
     * @param descriptor the resource template descriptor to register
     * @param handler the handler for the registered resource template
     * @return this registry
     */
    Resources registerTemplate(ResourceTemplateDescriptor descriptor, ResourceHandler handler);

    /**
     * Registers a resource template configured through its builder.
     *
     * @param configurer configures the resource template descriptor
     * @param handler    handles requests for the registered resource template
     * @return this resource registry
     */
    default Resources registerTemplate(
            Consumer<ResourceTemplateDescriptor.Builder> configurer, ResourceHandler handler) {
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
    default Resources registerTemplateAsync(ResourceTemplateDescriptor descriptor, AsyncResourceHandler handler) {
        return registerTemplate(descriptor, handler);
    }

    /**
     * Registers a resource template configured through its builder.
     *
     * @param descriptor a consumer that configures the resource template builder
     * @param handler    the asynchronous handler for the registered resource template
     * @return this resource registry
     */
    default Resources registerTemplateAsync(
            Consumer<ResourceTemplateDescriptor.Builder> descriptor, AsyncResourceHandler handler) {
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
