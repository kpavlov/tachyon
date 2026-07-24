/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import org.immutables.value.Value;

/**
 * A single message within a prompt, associating a {@link Role} with its {@link ContentBlock}.
 *
 * <p>Use the {@link #user(String)} or {@link #user(ContentBlock)} factories to quickly
 * construct a user-role message without specifying the role explicitly.
 */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public interface PromptMessage {

    Role role();

    ContentBlock content();

    static Builder builder() {
        return DefaultPromptMessage.builder();
    }

    static PromptMessage of(Role role, ContentBlock content) {
        return DefaultPromptMessage.of(role, content);
    }

    /** Creates a user-role message wrapping the given content block. */
    static PromptMessage user(ContentBlock content) {
        return DefaultPromptMessage.of(Role.USER, content);
    }

    /** Creates a user-role message whose content is plain text (no annotations). */
    static PromptMessage user(String text) {
        return DefaultPromptMessage.of(Role.USER, TextContent.of(text));
    }

    interface Builder {
        Builder role(Role role);

        Builder content(ContentBlock content);

        PromptMessage build();
    }
}
