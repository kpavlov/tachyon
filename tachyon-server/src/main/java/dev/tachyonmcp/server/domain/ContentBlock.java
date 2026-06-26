/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

/**
 * A piece of content in a tool result, prompt message, or resource.
 *
 * <p>Each variant carries content in a different media type. The sealed hierarchy ensures
 * exhaustive pattern-matching — all concrete types are known at compile time. The
 * {@code type()} method derives the MCP protocol discriminator from the variant type,
 * so callers never need to supply or validate it.
 */
public sealed interface ContentBlock extends HasMeta
        permits TextContent, ImageContent, AudioContent, ResourceLink, EmbeddedResource {

    /**
     * MCP protocol type discriminator for content block variants.
     *
     * <p>Each enum constant carries the wire-level discriminator string used in the
     * protocol JSON. This is the single source of truth — all mapping code references
     * {@link #discriminator()} rather than repeating raw strings.
     */
    enum Type {
        TEXT("text"),
        IMAGE("image"),
        AUDIO("audio"),
        RESOURCE_LINK("resource_link"),
        RESOURCE("resource");

        private final String discriminator;

        Type(String discriminator) {
            this.discriminator = discriminator;
        }

        /** Returns the wire-level discriminator string for this content type. */
        public String discriminator() {
            return discriminator;
        }
    }

    /** Returns the MCP protocol type discriminator for this content block variant. */
    Type type();
}
