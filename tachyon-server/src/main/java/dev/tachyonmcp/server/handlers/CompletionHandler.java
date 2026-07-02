/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.session.DispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.List;
import java.util.Map;

public final class CompletionHandler implements RpcMethodHandler {

    public CompletionHandler() {}

    @Override
    public String method() {
        return "completion/complete";
    }

    @Override
    public Object handle(DispatchContext context, Object params) {
        var paramsMap = params instanceof Map<?, ?> m ? m : Map.<String, Object>of();
        var ref = paramsMap.get("ref");
        var argument = paramsMap.get("argument");
        if (ref == null) {
            return JsonRpcErrors.invalidParams("Missing ref parameter");
        }
        if (!(ref instanceof Map<?, ?>)) {
            return JsonRpcErrors.invalidParams("Invalid ref parameter");
        }
        if (argument == null) {
            return JsonRpcErrors.invalidParams("Missing argument parameter");
        }
        if (!(argument instanceof Map<?, ?>)) {
            return JsonRpcErrors.invalidParams("Invalid argument parameter");
        }
        return context.responseMapper().completeResult(List.of(), null, false);
    }
}
