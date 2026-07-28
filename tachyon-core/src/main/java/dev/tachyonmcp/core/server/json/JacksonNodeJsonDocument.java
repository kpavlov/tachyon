/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.json;

import tools.jackson.databind.JsonNode;

record JacksonNodeJsonDocument(JsonNode node) implements JacksonNodeBacked {}
