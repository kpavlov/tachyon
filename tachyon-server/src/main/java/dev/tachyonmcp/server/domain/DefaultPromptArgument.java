/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

record DefaultPromptArgument(
        String name,
        @Nullable String title,
        @Nullable String description,
        @Nullable Boolean required) implements PromptArgument {}
