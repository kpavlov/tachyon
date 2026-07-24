package dev.tachyonmcp.server.json;

final class JsonDocuments {

    private JsonDocuments() {}

    static String requireContent(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("json must not be null or blank");
        }
        return json;
    }
}
