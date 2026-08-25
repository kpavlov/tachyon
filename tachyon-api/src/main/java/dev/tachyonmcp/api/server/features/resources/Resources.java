/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.resources;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Façade interface for MCP resources
 */
public interface Resources {

    /**
     * Registers a resource descriptor with its synchronous function.
     *
     * <p>A resource's {@link ResourceDescriptor#uri() uri} is its identity. {@link
     * ResourceDescriptor#name() name} is a display label, not identity, and MAY repeat across
     * resources — for example, the same skill mounted under two different namespace prefixes. Two
     * resources that share a name but have distinct URIs both remain registered; neither replaces
     * the other.
     *
     * <p>Registration is keyed on URI, not name:
     *
     * <ul>
     *   <li>Registering a URI already known under the <em>same</em> name replaces that resource in
     *       place (e.g. updated content or handler).
     *   <li>Registering a URI already known under a <em>different</em> name is rejected — one URI
     *       naming two unrelated resources is a genuine identity conflict, not a label collision.
     * </ul>
     *
     * @param descriptor the resource descriptor to register
     * @param fn         the resource function
     * @return this registry
     * @throws IllegalArgumentException if the URI is already registered under a different name
     */
    Resources register(ResourceDescriptor descriptor, ResourceFn fn);

    /**
     * Registers a resource configured through a descriptor builder.
     *
     * @param configurer configures the resource descriptor
     * @param fn         the resource function
     * @return this resource registry
     */
    default Resources register(Consumer<ResourceDescriptor.Builder> configurer, ResourceFn fn) {
        final var builder = ResourceDescriptor.builder();
        configurer.accept(builder);
        return register(builder.build(), fn);
    }

    /**
     * Registers a resource descriptor with an asynchronous function.
     *
     * @param descriptor the resource descriptor
     * @param fn         the asynchronous resource function
     * @return this resource registry for method chaining
     */
    Resources registerAsync(ResourceDescriptor descriptor, AsyncResourceFn fn);

    /**
     * Registers an asynchronous resource function using a descriptor configured through a builder.
     *
     * @param descriptor a consumer that configures the resource descriptor builder
     * @param fn         the asynchronous function for the resource
     * @return this resource registry
     */
    default Resources registerAsync(Consumer<ResourceDescriptor.Builder> descriptor, AsyncResourceFn fn) {
        final var builder = ResourceDescriptor.builder();
        descriptor.accept(builder);
        return registerAsync(builder.build(), fn);
    }

    /**
     * Removes a registered resource with the specified name.
     *
     * <p>{@code name} is not guaranteed unique — see {@link #register}. If more than one resource
     * shares {@code name}, one of them is removed; which one is unspecified. Prefer {@link
     * #unregisterByUri} when the URI is known.
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
     * <p>{@code name} is not guaranteed unique — see {@link #register}. If more than one resource
     * shares {@code name}, one of them is returned; which one is unspecified. Prefer {@link
     * #findByUri} when the URI is known.
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
     * Registers a resource template descriptor with its synchronous function.
     *
     * @param descriptor the resource template descriptor to register
     * @param fn the resource function
     * @return this registry
     */
    Resources registerTemplate(ResourceTemplateDescriptor descriptor, ResourceFn fn);

    /**
     * Registers a resource template configured through its builder.
     *
     * @param configurer configures the resource template descriptor
     * @param fn         the resource function
     * @return this resource registry
     */
    default Resources registerTemplate(Consumer<ResourceTemplateDescriptor.Builder> configurer, ResourceFn fn) {
        final var builder = ResourceTemplateDescriptor.builder();
        configurer.accept(builder);
        return registerTemplate(builder.build(), fn);
    }

    /**
     * Registers a resource template with an asynchronous function.
     *
     * @param descriptor the resource template descriptor to register
     * @param fn         the asynchronous function for the resource template
     * @return this resource registry
     */
    Resources registerTemplateAsync(ResourceTemplateDescriptor descriptor, AsyncResourceFn fn);

    /**
     * Registers a resource template configured through its builder.
     *
     * @param descriptor a consumer that configures the resource template builder
     * @param fn         the asynchronous function for the registered resource template
     * @return this resource registry
     */
    default Resources registerTemplateAsync(
            Consumer<ResourceTemplateDescriptor.Builder> descriptor, AsyncResourceFn fn) {
        final var builder = ResourceTemplateDescriptor.builder();
        descriptor.accept(builder);
        return registerTemplateAsync(builder.build(), fn);
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
