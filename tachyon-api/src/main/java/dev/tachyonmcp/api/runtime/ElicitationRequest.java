/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.api.runtime;

import dev.tachyonmcp.api.json.JsonSchema;

/**
 * A request to elicit additional information from the user via the client, in form mode.
 *
 * @param message the message to present to the user describing what information is being requested
 * @param requestedSchema a restricted JSON Schema (top-level primitive properties only) describing the requested form
 */
public record ElicitationRequest(String message, JsonSchema requestedSchema) {}
