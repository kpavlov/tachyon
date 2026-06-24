/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server;

public record SchemaValidationError(String path, String keyword, String message) {}
