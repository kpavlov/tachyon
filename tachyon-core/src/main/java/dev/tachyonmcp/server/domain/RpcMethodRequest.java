/* Copyright (c) 2026 Konstantin Pavlov and contributors. */

package dev.tachyonmcp.server.domain;

import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Requests user input by invoking another RPC method. */
@Value.Immutable
@Value.Style(
        allParameters = true,
        visibility = Value.Style.ImplementationVisibility.PACKAGE,
        typeImmutable = "Default*")
public non-sealed interface RpcMethodRequest extends InputRequest {

    /** The RPC method to invoke for input. */
    String method();

    /** Optional parameters for the RPC method. */
    @Nullable
    JsonNode params();

    @Value.Check
    default void check() {
        if (method().isBlank()) throw new IllegalArgumentException("method must not be blank");
    }

    static Builder builder() {
        return DefaultRpcMethodRequest.builder();
    }

    static RpcMethodRequest of(String method, @Nullable JsonNode params) {
        return DefaultRpcMethodRequest.of(method, params);
    }

    interface Builder {
        Builder method(String method);

        Builder params(@Nullable JsonNode params);

        RpcMethodRequest build();
    }
}
