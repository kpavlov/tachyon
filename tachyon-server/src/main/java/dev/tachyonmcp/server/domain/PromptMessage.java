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
public interface PromptMessage {

    Role role();

    ContentBlock content();

    static PromptMessage of(Role role, ContentBlock content) {
        return new DefaultPromptMessage(role, content);
    }

    /** Creates a user-role message wrapping the given content block. */
    static PromptMessage user(ContentBlock content) {
        return new DefaultPromptMessage(Role.USER, content);
    }

    /** Creates a user-role message whose content is plain text (no annotations). */
    static PromptMessage user(String text) {
        return new DefaultPromptMessage(Role.USER, TextContent.of(text));
    }
}
