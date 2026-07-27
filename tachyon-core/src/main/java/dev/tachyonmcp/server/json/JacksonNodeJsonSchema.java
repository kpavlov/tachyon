/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.json.JsonSchema;
import tools.jackson.databind.JsonNode;

record JacksonNodeJsonSchema(JsonNode node) implements JsonSchema, JacksonNodeBacked {}
