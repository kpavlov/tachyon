/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.transport.jsonrpc;

import dev.tachyonmcp.server.json.JsonUtils;
import java.util.Map;

/** Standard JSON-RPC error codes and factory methods. */
public final class JsonRpcErrors {

    /** Invalid JSON was received. */
    public static final int PARSE_ERROR = -32700;
    /** The JSON sent is not a valid Request object. */
    public static final int INVALID_REQUEST = -32600;
    /** The method does not exist / is not available. */
    public static final int METHOD_NOT_FOUND = -32601;
    /** Invalid method parameter(s). */
    public static final int INVALID_PARAMS = -32602;
    /** Internal JSON-RPC error. */
    public static final int INTERNAL_ERROR = -32603;
    /** Resource not found (custom error). */
    public static final int RESOURCE_NOT_FOUND = -32002;

    private JsonRpcErrors() {}

    /** Creates a parse error. */
    public static JsonRpcError parseError() {
        return new JsonRpcError(PARSE_ERROR, "Parse error");
    }

    /** Creates an invalid-request error with the given detail. */
    public static JsonRpcError invalidRequest(String detail) {
        return new JsonRpcError(INVALID_REQUEST, detail);
    }

    /** Creates a method-not-found error with the given detail. */
    public static JsonRpcError methodNotFound(String detail) {
        return new JsonRpcError(METHOD_NOT_FOUND, detail);
    }

    /** Creates an invalid-params error with the given detail. */
    public static JsonRpcError invalidParams(String detail) {
        return new JsonRpcError(INVALID_PARAMS, detail);
    }

    /** Creates an internal-error with the given detail. */
    public static JsonRpcError internalError(String detail) {
        return new JsonRpcError(INTERNAL_ERROR, detail);
    }

    /** Creates a resource-not-found error with the given detail. */
    public static JsonRpcError resourceNotFound(String detail) {
        return new JsonRpcError(RESOURCE_NOT_FOUND, detail);
    }

    public static JsonRpcError resourceNotFound(String detail, Map<String, String> data) {
        return new JsonRpcError(RESOURCE_NOT_FOUND, detail, JsonUtils.writeString(data));
    }
}
