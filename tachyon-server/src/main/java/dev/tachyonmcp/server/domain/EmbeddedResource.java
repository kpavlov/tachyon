/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * A complete resource embedded inline within a tool result, prompt, or other content.
 *
 * <p>The embedded {@code resource} contains both the URI and the actual content
 * ({@link TextResourceContents} or {@link BlobResourceContents}), allowing the
 * server to attach resource data directly without requiring a separate read round-trip.
 */
public record EmbeddedResource(
        ResourceContents resource, @Nullable Annotations annotations) implements ContentBlock {}
