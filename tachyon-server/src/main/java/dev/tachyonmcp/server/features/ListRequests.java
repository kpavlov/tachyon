/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Shared parsing of limit/cursor from JSON-RPC request params. */
public final class ListRequests {

    private ListRequests() {}

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
