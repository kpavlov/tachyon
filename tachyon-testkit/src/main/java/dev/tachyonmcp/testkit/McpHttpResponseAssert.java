/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import dev.tachyonmcp.testkit.JsonRpcResponseAssert.JsonRpcErrorAssert;
import dev.tachyonmcp.testkit.JsonRpcResponseAssert.JsonRpcSuccessAssert;
import java.net.http.HttpResponse;
import org.assertj.core.api.AbstractAssert;

/**
 * Assertions over a raw MCP HTTP response, for the cases {@link JsonRpcResponseAssert} alone cannot
 * cover: the HTTP status, and rejections the transport answers in plain text before a JSON-RPC
 * envelope exists (a duplicate header, an unusable protocol version, an oversized body).
 *
 * <p>Chains into the JSON-RPC assertions once the status is checked:
 *
 * <pre>{@code
 * assertThatResponse(response).hasStatus(400).isJsonRpcError().hasId(9).hasErrorCode(-32020);
 * assertThatResponse(response).isRejectedWith(400, "Duplicate MCP header");
 * }</pre>
 */
public final class McpHttpResponseAssert extends AbstractAssert<McpHttpResponseAssert, HttpResponse<String>> {

    private McpHttpResponseAssert(HttpResponse<String> response) {
        super(response, McpHttpResponseAssert.class);
    }

    /**
     * Creates assertions for an MCP HTTP response.
     *
     * @param response the HTTP response
     * @return assertions over that response
     */
    public static McpHttpResponseAssert assertThatResponse(HttpResponse<String> response) {
        return new McpHttpResponseAssert(response);
    }

    /**
     * Verifies the HTTP status, reporting the body on failure — it usually says why.
     *
     * @param expected the expected HTTP status code
     * @return this assertion for chaining
     */
    public McpHttpResponseAssert hasStatus(int expected) {
        isNotNull();
        if (actual.statusCode() != expected) {
            failWithMessage("Expected HTTP status <%d> but was <%d>: %s", expected, actual.statusCode(), actual.body());
        }
        return this;
    }

    /**
     * Verifies a plain-text transport rejection: the status, and the exact body. Asserting the body
     * matters — a JSON-RPC error carries the same status, so the status alone would not prove which
     * layer rejected the request.
     *
     * @param expectedStatus the expected HTTP status code
     * @param expectedBody   the expected plain-text body
     * @return this assertion for chaining
     */
    public McpHttpResponseAssert isRejectedWith(int expectedStatus, String expectedBody) {
        hasStatus(expectedStatus);
        if (!expectedBody.equals(actual.body())) {
            failWithMessage("Expected plain-text body <%s> but was <%s>", expectedBody, actual.body());
        }
        return this;
    }

    /**
     * Verifies the body is a JSON-RPC error and returns error-only assertions.
     *
     * @return assertions for the JSON-RPC error branch
     */
    public JsonRpcErrorAssert isJsonRpcError() {
        isNotNull();
        return JsonRpcResponseAssert.assertThat(actual).isJsonRpcError();
    }

    /**
     * Verifies the body is a successful JSON-RPC response and returns success-only assertions.
     *
     * @return assertions for the JSON-RPC success branch
     */
    public JsonRpcSuccessAssert isSuccess() {
        isNotNull();
        return JsonRpcResponseAssert.assertThat(actual).isSuccess();
    }
}
