/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * Describes a single argument accepted by a prompt template.
 *
 * <p>Arguments are matched by {@code name}. The {@code required} flag tells the client
 * whether the argument must be provided; when absent or {@code null}, the argument is
 * considered optional.
 */
public record PromptArgument(
        String name,
        @Nullable String title,
        @Nullable String description,
        @Nullable Boolean required) {}
