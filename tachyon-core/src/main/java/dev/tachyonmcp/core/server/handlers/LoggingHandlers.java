/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server.handlers;

import dev.tachyonmcp.api.server.domain.LoggingLevel;
import dev.tachyonmcp.core.server.RpcMethodHandler;
import dev.tachyonmcp.core.server.session.DispatchContext;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class LoggingHandlers {

    private LoggingHandlers() {}

    public static void register(Map<String, RpcMethodHandler<?, ?>> registry) {
        registry.put("logging/setLevel", new SetLevelHandler());
    }

    private static class SetLevelHandler implements RpcMethodHandler<LoggingLevel, Object> {

        @Override
        public String method() {
            return "logging/setLevel";
        }

        @Override
        public LoggingLevel decode(DispatchContext context, @Nullable Object rawParams) {
            return context.requestMapper().loggingLevel(rawParams);
        }

        @Override
        public Object handle(DispatchContext context, LoggingLevel level) {
            context.setLoggingLevel(level);
            return context.responseMapper().emptyResult();
        }
    }
}
