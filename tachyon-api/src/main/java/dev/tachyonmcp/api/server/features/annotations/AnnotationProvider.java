/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.annotations;

import dev.tachyonmcp.api.annotations.ExperimentalApi;

/**
 * Strategy interface for interpreting annotation programming models and registering the
 * discovered Tachyon tools, resources, or prompts.
 *
 * <p>Each implementation knows how to inspect one particular annotation framework (e.g.
 * mcp-java, LangChain4j) and translate the annotated methods into standard Tachyon feature
 * registrations via the {@link AnnotationRegistrationContext}.
 *
 * <p>Implementations are stateless and reusable — the same provider instance may be passed to
 * multiple {@link #register} calls for different application objects.
 *
 * <p>Third-party integration modules supply their own implementations. Tachyon core never
 * references framework-specific annotations.
 *
 * <p><b>Example</b></p>
 *
 * <pre>{@code
 * public class McpJavaAnnotationProvider implements AnnotationProvider {
 *     @Override
 *     public void register(Object instance, AnnotationRegistrationContext context) {
 *         // scan instance for @Tool, @Resource, @Prompt ...
 *         // register via context.tools().register(...), etc.
 *     }
 * }
 * }</pre>
 */
@ExperimentalApi
public interface AnnotationProvider {

    /**
     * Inspects {@code instance} for annotated methods and registers the resulting tools,
     * resources, and/or prompts through {@code context}.
     *
     * <p>Implementations should fail fast (throw) when encountering:
     *
     * <ul>
     *   <li>duplicate feature names within the same provider
     *   <li>invalid annotation combinations
     *   <li>unsupported method signatures
     * </ul>
     *
     * @param instance the application object whose annotated methods to scan
     * @param context  the registration façade
     * @throws IllegalArgumentException if the instance contains invalid annotations
     * @throws IllegalStateException    if registration fails for any reason
     */
    void register(Object instance, AnnotationRegistrationContext context);
}
