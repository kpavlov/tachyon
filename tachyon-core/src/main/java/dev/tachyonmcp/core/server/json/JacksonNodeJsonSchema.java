/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import dev.tachyonmcp.api.json.JsonSchema;
import tools.jackson.databind.JsonNode;

record JacksonNodeJsonSchema(JsonNode node) implements JsonSchema, JacksonNodeBacked {}
