/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.runtime;

import dev.tachyonmcp.api.server.domain.Args;
import org.jspecify.annotations.Nullable;

/**
 * The client's response to an elicitation request.
 *
 * @param action the user's action in response to the elicitation
 * @param content the submitted form data, present only when {@code action} is {@link Action#ACCEPT}
 */
public record ElicitationResult(Action action, @Nullable Args content) {

    /** The user action in response to an elicitation request. */
    public enum Action {
        /** The user submitted the form. */
        ACCEPT,
        /** The user explicitly declined the action. */
        DECLINE,
        /** The user dismissed the request without making an explicit choice. */
        CANCEL
    }
}
