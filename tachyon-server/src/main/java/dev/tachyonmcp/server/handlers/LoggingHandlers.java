/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.LoggingLevelMapper;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.codecs.ProtocolCodecUtil;
import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.SetLevelRequestParams;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcCodec;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
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
            SetLevelRequestParams typed;
            if (params instanceof SetLevelRequestParams p) {
                typed = p;
            } else if (params instanceof Map<?, ?> map) {
                try {
                    var json = JsonRpcCodec.writeValueAsString(map);
                    typed = ProtocolCodecUtil.decodeWithCodec(json, SetLevelRequestParams.class);
                } catch (RuntimeException e) {
                    return JsonRpcErrors.invalidParams("Failed to decode params for logging/setLevel");
                }
            } else {
                return JsonRpcErrors.invalidRequest("Invalid params for logging/setLevel");
            }

            var protocolLevel = typed.level();
            if (protocolLevel == null) {
                return JsonRpcErrors.invalidRequest("Missing level parameter");
            }

            context.setLoggingLevel(LoggingLevelMapper.toDomain(protocolLevel));

            return context.responseMapper().emptyResult();
        }
    }
}
