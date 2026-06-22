/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * A plain-text content block provided to or from an LLM.
 *
 * <p>Text is the most common content type. Optional {@link Annotations} allow the
 * server to hint at audience, priority, or modification time.
 */
public record TextContent(String text, @Nullable Annotations annotations) implements ContentBlock {}
