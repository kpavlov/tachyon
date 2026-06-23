/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * Hints about tool behaviour that clients may use for safety or UX decisions.
 *
 * <p>All fields are optional — when {@code null} the client should
 * make no assumptions about the corresponding property. {@code readOnlyHint} marks
 * tools that do not modify state, {@code destructiveHint} warns about irreversible
 * changes, {@code idempotentHint} indicates safe retries, and {@code openWorldHint}
 * signals that the tool may reach outside the MCP ecosystem.
 */
public interface ToolAnnotations {

    @Nullable
    String title();

    @Nullable
    Boolean readOnlyHint();

    @Nullable
    Boolean destructiveHint();

    @Nullable
    Boolean idempotentHint();

    @Nullable
    Boolean openWorldHint();

    static ToolAnnotations of(
            @Nullable String title,
            @Nullable Boolean readOnlyHint,
            @Nullable Boolean destructiveHint,
            @Nullable Boolean idempotentHint,
            @Nullable Boolean openWorldHint) {
        return new DefaultToolAnnotations(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint);
    }
}
