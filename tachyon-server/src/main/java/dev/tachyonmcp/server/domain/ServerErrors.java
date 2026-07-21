/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.domain;

import java.util.Map;

/** Factories for protocol-neutral server errors. */
public final class ServerErrors {

    private ServerErrors() {}

    public static ServerError parseError() {
        return new ServerError(ServerError.Kind.PARSE_ERROR, "Parse error");
    }

    public static ServerError invalidRequest(String detail) {
        return new ServerError(ServerError.Kind.INVALID_REQUEST, detail);
    }

    public static ServerError methodNotFound(String detail) {
        return new ServerError(ServerError.Kind.METHOD_NOT_FOUND, detail);
    }

    public static ServerError invalidParams(String detail) {
        return new ServerError(ServerError.Kind.INVALID_PARAMS, detail);
    }

    public static ServerError internalError(String detail) {
        return new ServerError(ServerError.Kind.INTERNAL_ERROR, detail);
    }

    public static ServerError resourceNotFound(String detail) {
        return new ServerError(ServerError.Kind.RESOURCE_NOT_FOUND, detail);
    }

    public static ServerError resourceNotFound(String detail, Map<String, String> data) {
        return new ServerError(ServerError.Kind.RESOURCE_NOT_FOUND, detail, data);
    }

    public static ServerError headerMismatch(String detail) {
        return new ServerError(ServerError.Kind.HEADER_MISMATCH, detail);
    }

    /** {@code error.data.requiredCapabilities} is a {@code ClientCapabilities}-shaped map, not a name list. */
    public static ServerError missingRequiredClientCapability(String detail, Map<String, Object> requiredCapabilities) {
        return new ServerError(
                ServerError.Kind.MISSING_REQUIRED_CLIENT_CAPABILITY,
                detail,
                Map.of("requiredCapabilities", requiredCapabilities));
    }
}
