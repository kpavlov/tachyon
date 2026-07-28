/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.completions;

/**
 * Façade interface for {@code completion/complete} providers, keyed by the MCP reference they answer for:
 * a prompt name ({@code ref/prompt}) or a resource URI / resource-template URI ({@code
 * ref/resource}, matched verbatim against the URI the client sends — the same string returned by
 * {@code resources/templates/list}).
 *
 * <p>A ref with no registered handler yields an empty completion result rather than an error —
 * the MCP spec does not require completions to be exhaustively declared.
 */
public interface Completions {

    /**
     * Registers a completion function for a prompt's arguments.
     *
     * @param promptName the prompt name, as declared in its {@code PromptDescriptor}
     * @param fn the completion function
     * @return this registry
     */
    Completions registerForPrompt(String promptName, CompletionFn fn);

    /**
     * Registers an asynchronous completion function for a prompt's arguments.
     *
     * @param promptName the prompt name
     * @param fn the asynchronous completion function
     * @return this registry
     */
    Completions registerForPromptAsync(String promptName, AsyncCompletionFn fn);

    /**
     * Registers a completion function for a resource or resource-template's variables.
     *
     * @param uriOrTemplate the resource URI, or the resource template's {@code uriTemplate}
     * @param fn the completion function
     * @return this registry
     */
    Completions registerForResource(String uriOrTemplate, CompletionFn fn);

    /**
     * Registers an asynchronous completion function for a resource or resource-template's variables.
     *
     * @param uriOrTemplate the resource URI, or the resource template's {@code uriTemplate}
     * @param fn the asynchronous completion function
     * @return this registry
     */
    Completions registerForResourceAsync(String uriOrTemplate, AsyncCompletionFn fn);

    /**
     * Removes the completion function registered for the specified prompt name.
     *
     * @param promptName the prompt name
     * @return {@code true} if a function was removed, {@code false} otherwise
     */
    boolean unregisterForPrompt(String promptName);

    /**
     * Removes the completion function registered for the specified resource URI or template.
     *
     * @param uriOrTemplate the resource URI or template
     * @return {@code true} if a function was removed, {@code false} otherwise
     */
    boolean unregisterForResource(String uriOrTemplate);
}
