/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

/**
 * Describes a single argument accepted by a prompt template.
 *
 * <p>Arguments are matched by {@code name}. The {@code required} flag tells the client
 * whether the argument must be provided; when absent or {@code null}, the argument is
 * considered optional.
 */
@Value.Immutable
@Value.Builder
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface PromptArgument {

    String name();

    @Nullable
    String title();

    @Nullable
    String description();

    @Nullable
    Boolean required();

    static DefaultPromptArgument.Builder builder() {
        return DefaultPromptArgument.builder();
    }

    static PromptArgument of(
            String name, @Nullable String title, @Nullable String description, @Nullable Boolean required) {
        return DefaultPromptArgument.of(name, title, description, required);
    }
}
