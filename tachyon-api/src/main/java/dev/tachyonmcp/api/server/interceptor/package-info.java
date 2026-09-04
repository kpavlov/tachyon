/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

/**
 * The server's single cross-cutting seam: around-advice over every inbound MCP request and
 * notification.
 *
 * <p>Tracing, auditing, authorization, rate limiting and metrics are all expressed as an {@link
 * dev.tachyonmcp.api.server.interceptor.McpInterceptor}. There is deliberately no second
 * per-request hook type.
 */
@NullMarked
package dev.tachyonmcp.api.server.interceptor;

import org.jspecify.annotations.NullMarked;
