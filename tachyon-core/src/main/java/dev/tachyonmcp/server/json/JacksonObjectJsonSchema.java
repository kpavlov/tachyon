/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

import dev.tachyonmcp.protocol.api.json.JsonSchema;
import tools.jackson.databind.node.ObjectNode;

record JacksonObjectJsonSchema(ObjectNode node) implements JsonSchema, JacksonNodeBacked {}
