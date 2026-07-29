/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.core.protocol.RequestMappingException;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.Map;

public final class LoggingHandlers {

    private LoggingHandlers() {}

    public static void register(Map<String, RpcMethodHandler> registry) {
        registry.put("logging/setLevel", new SetLevelHandler());
    }

    private static class SetLevelHandler implements RpcMethodHandler {

        @Override
        public String method() {
            return "logging/setLevel";
        }

        @Override
        public Object handle(DispatchContext context, Object params) {
            try {
                context.setLoggingLevel(context.requestMapper().loggingLevel(params));
            } catch (RequestMappingException e) {
                return e.error();
            }
            return context.responseMapper().emptyResult();
        }
    }
}
