/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

record DefaultToolAnnotations(
        @Nullable String title,
        @Nullable Boolean readOnlyHint,
        @Nullable Boolean destructiveHint,
        @Nullable Boolean idempotentHint,
        @Nullable Boolean openWorldHint)
        implements ToolAnnotations {}
