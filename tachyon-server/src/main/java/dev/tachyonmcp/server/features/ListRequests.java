/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features;

import dev.tachyonmcp.annotations.InternalApi;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Shared parsing of limit/cursor from JSON-RPC request params. */
@InternalApi
public final class ListRequests {

    private ListRequests() {}

    /**
     * Parses the {@code inputResponses} value of a request into a name→node map, re-encoding each
     * raw value through the codec. Returns {@code null} when absent or empty. Pass
     * {@code map.get("inputResponses")}.
     */
    public static @Nullable Map<String, JsonNode> extractInputResponses(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) return null;
        var result = new LinkedHashMap<String, JsonNode>();
        for (var entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String k) {
                result.put(k, ProtocolCodecUtil.parseJsonNode(JsonRpcCodec.writeValueAsString(entry.getValue())));
            }
        }
        return result.isEmpty() ? null : result;
    }

    /** Extracts the limit parameter from a Map or typed request object. */
    public static int parseLimit(Object params) {
        if (params instanceof Map<?, ?> map && map.get("limit") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    /** Extracts the cursor parameter from a Map or typed request object. */
    public static @Nullable String parseCursor(Object params) {
        if (params instanceof Map<?, ?> map && map.get("cursor") instanceof String s) {
            return s;
        }
        return null;
    }
}
