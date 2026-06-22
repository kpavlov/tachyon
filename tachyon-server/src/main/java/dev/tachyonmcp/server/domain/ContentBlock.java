/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.domain;

/**
 * A piece of content in a tool result, prompt message, or resource.
 *
 * <p>Each variant carries content in a different media type. The sealed hierarchy ensures
 * exhaustive pattern-matching — all concrete types are known at compile time.
 */
public sealed interface ContentBlock permits TextContent, ImageContent, AudioContent, ResourceLink, EmbeddedResource {}
