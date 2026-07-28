/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

/**
 * Runtime context, session management, and SSE connection abstractions for
 * request processing. Provides {@link dev.tachyonmcp.core.runtime.RequestContext},
 * {@link dev.tachyonmcp.core.runtime.Session}, and related types for tracking
 * interaction state, backpressure, and server-sent event delivery across
 * MCP client sessions.
 */
@NullMarked
package dev.tachyonmcp.core.runtime;

import org.jspecify.annotations.NullMarked;
