/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.server.McpMethodHandler;
import java.util.Map;

public final class CompletionHandlers {

    private CompletionHandlers() {}

    public static void register(Map<String, McpMethodHandler> registry) {
        registry.put("completion/complete", new CompletionHandler());
    }
}
