/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.completions;

import dev.tachyonmcp.api.server.ServerFeature;
import java.util.Map;
import java.util.Objects;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * A {@code completion/complete} request: the argument being completed and any
 * previously-resolved sibling arguments supplied as context.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface CompletionRequest extends ServerFeature.Request {

    /**
     * The name of the argument being completed.
     */
    String argumentName();

    /**
     * The current (partial) value typed for the argument.
     */
    String argumentValue();

    @Nullable
    @Override
    Map<String, Object> meta();

    /**
     * Previously-resolved argument name/value pairs, or empty if none.
     */
    @Value.Default
    default Map<String, String> resolvedArguments() {
        return Map.of();
    }

    @Value.Check
    default void check() {
        if (argumentName().isBlank()) throw new IllegalArgumentException("argumentName must not be blank");
        Objects.requireNonNull(argumentValue(), "argumentValue must not be null");
    }

    static CompletionRequest of(String argumentName, String argumentValue, Map<String, String> resolvedArguments) {
        return DefaultCompletionRequest.builder()
                .argumentName(argumentName)
                .argumentValue(argumentValue)
                .resolvedArguments(resolvedArguments)
                .build();
    }

    static Builder builder() {
        return DefaultCompletionRequest.builder();
    }

    interface Builder {
        /** Fills this builder with the attribute values from {@code instance}. */
        Builder from(CompletionRequest instance);

        Builder argumentName(String argumentName);

        Builder argumentValue(String argumentValue);

        Builder resolvedArguments(Map<String, ? extends String> resolvedArguments);

        Builder meta(@Nullable Map<String, ?> entries);

        CompletionRequest build();
    }
}
