/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.json;

/**
 * A single schema validation error.
 *
 * @param path    JSON pointer to the failing field
 * @param keyword the validation keyword that failed
 * @param message human-readable error description
 */
public record SchemaValidationError(String path, String keyword, String message) {}
