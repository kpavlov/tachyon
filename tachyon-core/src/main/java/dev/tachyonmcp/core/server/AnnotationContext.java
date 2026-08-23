/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import dev.tachyonmcp.api.annotations.ExperimentalApi;
import dev.tachyonmcp.api.server.features.annotations.AnnotationProvider;
import java.util.ArrayList;
import java.util.List;

/**
 * DSL entry point for registering annotated application objects through {@link
 * AnnotationProvider}s. Passed to the {@link ServerBuilder#annotations} configurer.
 *
 * <p>Each call to {@link #withProvider} sets the active provider; subsequent {@link #register}
 * calls dispatch through that provider until a new one is set. Multiple providers and multiple
 * objects are supported.
 *
 * <p>Example:
 *
 * <pre>{@code
 * TachyonServer.builder()
 *     .annotations(a -> a
 *         .withProvider(new McpJavaAnnotationProvider())
 *         .register(new WeatherService())
 *         .register(new CalculatorService()))
 *     .build();
 * }</pre>
 */
@ExperimentalApi
public final class AnnotationContext {

    private final List<Registration> registrations = new ArrayList<>();

    /** Sets the active annotation provider for subsequent {@link #register} calls. */
    public AnnotationContext withProvider(AnnotationProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        currentProvider = provider;
        return this;
    }

    /**
     * Registers {@code instance} using the current {@link #withProvider}. The provider inspects
     * the object for annotated methods and registers the resulting features through the
     * server's feature façades.
     *
     * @param instance the application object to scan
     * @return this context for chaining
     * @throws IllegalStateException if no provider has been set
     */
    public AnnotationContext register(Object instance) {
        if (instance == null) throw new IllegalArgumentException("instance must not be null");
        if (currentProvider == null) {
            throw new IllegalStateException("No annotation provider set. Call withProvider(...) before register(...).");
        }
        registrations.add(new Registration(currentProvider, instance));
        return this;
    }

    /** Returns all accumulated registrations for deferred execution. */
    List<Registration> registrations() {
        return List.copyOf(registrations);
    }

    private AnnotationProvider currentProvider;

    /** A pending provider + instance pair. */
    record Registration(AnnotationProvider provider, Object instance) {}
}
