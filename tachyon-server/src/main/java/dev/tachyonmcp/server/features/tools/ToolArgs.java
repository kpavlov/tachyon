/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.features.tools;

import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public final class ToolArgs {

    private final Map<String, JsonNode> raw;

    private ToolArgs(@Nullable Map<String, JsonNode> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    public static ToolArgs of(@Nullable Map<String, JsonNode> raw) {
        return new ToolArgs(raw);
    }

    public boolean isEmpty() {
        return raw.isEmpty();
    }

    public boolean has(String key) {
        return raw.containsKey(key);
    }

    public String string(String key) {
        return node(key).asString();
    }

    public int intValue(String key) {
        return node(key).asInt();
    }

    public boolean boolValue(String key) {
        return node(key).asBoolean();
    }

    public double doubleValue(String key) {
        return node(key).asDouble();
    }

    public Optional<String> stringOpt(String key) {
        return has(key) ? Optional.of(raw.get(key).asString()) : Optional.empty();
    }

    public String stringOr(String key, String fallback) {
        return has(key) ? raw.get(key).asString() : fallback;
    }

    public JsonNode node(String key) {
        var n = raw.get(key);
        if (n == null) throw new InvalidArgumentException(key, "required argument missing");
        return n;
    }

    @Nullable
    public JsonNode raw(String key) {
        return raw.get(key);
    }
}
