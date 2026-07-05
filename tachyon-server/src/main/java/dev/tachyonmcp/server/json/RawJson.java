/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.json;

/**
 * A pre-serialized JSON payload marker. When used as a
 * {@link dev.tachyonmcp.server.features.tools.ToolResult.Success#structuredValue()},
 * the string is parsed once at envelope encoding, skipping the Jackson value-to-tree
 * conversion of ordinary structured values.
 *
 * @param json the JSON string; must not be null or blank
 * @author Konstantin Pavlov
 */
public record RawJson(String json) {

    private static final int MAX_TO_STRING = 200;

    /** Compact constructor rejects null/blank. */
    public RawJson {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("json must not be null or blank");
        }
    }

    /** Creates a {@link RawJson} from a JSON string. */
    public static RawJson of(String json) {
        return new RawJson(json);
    }

    @Override
    public String toString() {
        return "RawJson[" + (json.length() <= MAX_TO_STRING ? json : json.substring(0, MAX_TO_STRING) + "…") + "]";
    }
}
