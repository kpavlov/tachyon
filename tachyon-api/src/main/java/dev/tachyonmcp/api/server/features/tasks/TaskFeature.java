/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.server.features.tasks;

import dev.tachyonmcp.api.annotations.ExperimentalApi;

/** Optional MCP task operations supported by a {@link TaskExecutionEngine}. */
@ExperimentalApi
public enum TaskFeature {
    /** Supports {@code tasks/list}. */
    LIST,
    /** Supports {@code tasks/cancel}. */
    CANCEL,
    /** Supports task-augmented requests and {@code tasks/update}. */
    REQUESTS
}
