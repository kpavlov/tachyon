/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.server.json;

record DefaultJsonSchema(String json) implements JsonSchema {

    static final JsonSchema OBJECT = new DefaultJsonSchema("{\"type\":\"object\"}");
}
