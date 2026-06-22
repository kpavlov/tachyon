/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.transport.jsonrpc;

public final class JsonRpcErrors {

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int RESOURCE_NOT_FOUND = -32002;

    private JsonRpcErrors() {}

    public static JsonRpcError parseError() {
        return new JsonRpcError(PARSE_ERROR, "Parse error");
    }

    public static JsonRpcError invalidRequest(String detail) {
        return new JsonRpcError(INVALID_REQUEST, detail);
    }

    public static JsonRpcError methodNotFound(String detail) {
        return new JsonRpcError(METHOD_NOT_FOUND, detail);
    }

    public static JsonRpcError invalidParams(String detail) {
        return new JsonRpcError(INVALID_PARAMS, detail);
    }

    public static JsonRpcError internalError(String detail) {
        return new JsonRpcError(INTERNAL_ERROR, detail);
    }

    public static JsonRpcError resourceNotFound(String detail) {
        return new JsonRpcError(RESOURCE_NOT_FOUND, detail);
    }
}
