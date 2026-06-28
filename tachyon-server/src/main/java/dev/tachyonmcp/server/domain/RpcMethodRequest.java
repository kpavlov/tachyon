/* Copyright (c) 2026 Konstantin Pavlov. */

package dev.tachyonmcp.server.domain;

import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface RpcMethodRequest extends InputRequest {

    String method();

    @Nullable
    JsonNode params();

    static DefaultRpcMethodRequest.Builder builder() {
        return DefaultRpcMethodRequest.builder();
    }

    static RpcMethodRequest of(String method, @Nullable JsonNode params) {
        return DefaultRpcMethodRequest.of(method, params);
    }
}
