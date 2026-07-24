package dev.tachyonmcp.server.json;

record DefaultJsonSchema(String json) implements JsonSchema {

    static final JsonSchema OBJECT = new DefaultJsonSchema("{\"type\":\"object\"}");
}
