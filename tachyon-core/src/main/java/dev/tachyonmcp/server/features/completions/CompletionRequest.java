/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.completions;

import java.util.Map;
import java.util.Objects;

/**
 * A single {@code completion/complete} request: the argument being completed and any
 * previously-resolved sibling arguments supplied as context.
 *
 * @param argumentName the name of the argument being completed
 * @param argumentValue the current (partial) value typed for the argument
 * @param resolvedArguments previously-resolved argument name/value pairs, or empty if none
 */
public record CompletionRequest(String argumentName, String argumentValue, Map<String, String> resolvedArguments) {

    public CompletionRequest {
        Objects.requireNonNull(argumentName, "argumentName");
        Objects.requireNonNull(argumentValue, "argumentValue");
        resolvedArguments = resolvedArguments != null ? Map.copyOf(resolvedArguments) : Map.of();
    }
}
