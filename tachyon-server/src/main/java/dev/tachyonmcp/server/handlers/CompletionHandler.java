/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.handlers;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CompleteResult;
import dev.tachyonmcp.server.JsonSchemaValidator;
import dev.tachyonmcp.server.McpMethodHandler;
import dev.tachyonmcp.server.session.McpContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.List;
import java.util.Map;

public final class CompletionHandler implements McpMethodHandler {

    private final JsonSchemaValidator validator;

    public CompletionHandler(JsonSchemaValidator validator) {
        this.validator = validator;
    }

    @Override
    public String method() {
        return "completion/complete";
    }

    @Override
    public Object handle(McpContext context, Object params) {
        var paramsMap = params instanceof Map<?, ?> m ? m : Map.<String, Object>of();
        var ref = paramsMap.get("ref");
        var argument = paramsMap.get("argument");
        if (ref == null) {
            return JsonRpcErrors.invalidParams("Missing ref parameter");
        }
        if (argument == null) {
            return JsonRpcErrors.invalidParams("Missing argument parameter");
        }
        return new CompleteResult(new CompleteResult.Completion(List.of(), 0.0, false), null, null);
    }
}
