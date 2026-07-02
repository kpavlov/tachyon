/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.server.RpcMethodHandler;
import java.util.Map;

public final class CompletionHandlers {

    private CompletionHandlers() {}

    public static void register(Map<String, RpcMethodHandler> registry) {
        registry.put("completion/complete", new CompletionHandler());
    }
}
