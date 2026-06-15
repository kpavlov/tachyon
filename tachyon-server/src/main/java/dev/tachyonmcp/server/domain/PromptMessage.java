/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

/**
 * A single message within a prompt, associating a {@link Role} with its {@link ContentBlock}.
 *
 * <p>Use the {@link #user(String)} or {@link #user(ContentBlock)} factories to quickly
 * construct a user-role message without specifying the role explicitly.
 */
public record PromptMessage(Role role, ContentBlock content) {

    /** Creates a user-role message wrapping the given content block. */
    public static PromptMessage user(ContentBlock content) {
        return new PromptMessage(Role.USER, content);
    }

    /** Creates a user-role message whose content is plain text (no annotations). */
    public static PromptMessage user(String text) {
        return new PromptMessage(Role.USER, new TextContent(text, null));
    }
}
