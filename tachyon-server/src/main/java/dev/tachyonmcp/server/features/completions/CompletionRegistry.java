/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.completions;

import java.util.Optional;

/**
 * Registry of {@code completion/complete} providers, keyed by the MCP reference they answer for:
 * a prompt name ({@code ref/prompt}) or a resource URI / resource-template URI ({@code
 * ref/resource}, matched verbatim against the URI the client sends — the same string returned by
 * {@code resources/templates/list}).
 *
 * <p>A ref with no registered handler yields an empty completion result rather than an error —
 * the MCP spec does not require completions to be exhaustively declared.
 */
public interface CompletionRegistry {

    /**
     * Registers a completion handler for a prompt's arguments.
     *
     * @param promptName the prompt name, as declared in its {@code PromptDescriptor}
     * @param handler the handler invoked for {@code ref/prompt} completion requests
     * @return this registry
     */
    CompletionRegistry registerForPrompt(String promptName, CompletionHandler handler);

    /**
     * Registers an asynchronous completion handler for a prompt's arguments.
     *
     * @param promptName the prompt name
     * @param handler the asynchronous completion handler
     * @return this registry
     */
    default CompletionRegistry registerForPromptAsync(String promptName, AsyncCompletionHandler handler) {
        return registerForPrompt(promptName, handler);
    }

    /**
     * Registers a completion handler for a resource or resource-template's variables.
     *
     * @param uriOrTemplate the resource URI, or the resource template's {@code uriTemplate}
     * @param handler the handler invoked for {@code ref/resource} completion requests
     * @return this registry
     */
    CompletionRegistry registerForResource(String uriOrTemplate, CompletionHandler handler);

    /**
     * Registers an asynchronous completion handler for a resource or resource-template's variables.
     *
     * @param uriOrTemplate the resource URI, or the resource template's {@code uriTemplate}
     * @param handler the asynchronous completion handler
     * @return this registry
     */
    default CompletionRegistry registerForResourceAsync(String uriOrTemplate, AsyncCompletionHandler handler) {
        return registerForResource(uriOrTemplate, handler);
    }

    /**
     * Removes the completion handler registered for the specified prompt name.
     *
     * @param promptName the prompt name
     * @return {@code true} if a handler was removed, {@code false} otherwise
     */
    boolean unregisterForPrompt(String promptName);

    /**
     * Removes the completion handler registered for the specified resource URI or template.
     *
     * @param uriOrTemplate the resource URI or template
     * @return {@code true} if a handler was removed, {@code false} otherwise
     */
    boolean unregisterForResource(String uriOrTemplate);

    /**
     * Finds the completion handler registered for a prompt name.
     *
     * @param promptName the prompt name
     * @return the matching handler, or an empty optional if none is registered
     */
    Optional<CompletionHandler> findForPrompt(String promptName);

    /**
     * Finds the completion handler registered for a resource URI or template.
     *
     * @param uriOrTemplate the resource URI or template
     * @return the matching handler, or an empty optional if none is registered
     */
    Optional<CompletionHandler> findForResource(String uriOrTemplate);
}
