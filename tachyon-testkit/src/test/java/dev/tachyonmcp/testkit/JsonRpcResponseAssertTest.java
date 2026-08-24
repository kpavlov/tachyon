/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThatJsonRpcResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Verifies JSON-RPC envelope branch assertions. */
class JsonRpcResponseAssertTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void identifiesSuccessEnvelope() {
        assertThatJsonRpcResponse("""
                {"jsonrpc":"2.0","id":1,"result":{"content":[]}}
                """).isSuccess();
    }

    @Test
    void identifiesErrorEnvelope() {
        assertThatJsonRpcResponse("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid params"}}
                """).isJsonRpcError();
    }

    @Test
    void successBranchExposesContentAssertions() {
        var text = JSON.readTree("{\"type\":\"text\",\"text\":\"done\"}");

        assertThatJsonRpcResponse("""
                {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"done"}],"resultType":"complete",
                "structuredContent":{"output":"success"}}}
                """)
                .isSuccess()
                .hasId(1)
                .hasContent()
                .hasContentExactly(text)
                .hasTextContent("done")
                .hasResultType("complete")
                .hasStructuredContent(JSON.readTree("{\"output\":\"success\"}"))
                .hasResult(JSON.readTree("""
                        {"content":[{"type":"text","text":"done"}],"resultType":"complete",
                         "structuredContent":{"output":"success"}}
                        """));
    }

    @Test
    void errorBranchExposesErrorAssertions() {
        assertThatJsonRpcResponse("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid params: name",
                "data":{"field":"name"}}}
                """)
                .isJsonRpcError()
                .hasId(1)
                .hasErrorCode(-32602)
                .hasErrorMessageContaining("Invalid params")
                .hasErrorDataSatisfying(
                        data -> assertThat(data.path("field").asString()).isEqualTo("name"))
                .hasError(JSON.readTree("""
                        {"code":-32602,"message":"Invalid params: name","data":{"field":"name"}}
                        """));
    }

    @Test
    void rejectsErrorEnvelopeAsSuccess() {
        assertThatThrownBy(() -> assertThatJsonRpcResponse("""
                            {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid params"}}
                            """).isSuccess()).isInstanceOf(AssertionError.class);
    }

    @Test
    void rejectsSuccessEnvelopeAsError() {
        assertThatThrownBy(() -> assertThatJsonRpcResponse("""
                            {"jsonrpc":"2.0","id":1,"result":{"content":[]}}
                            """).isJsonRpcError())
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void rejectsEnvelopeContainingResultAndError() {
        assertThatThrownBy(() -> assertThatJsonRpcResponse("""
                            {"jsonrpc":"2.0","id":1,"result":{},"error":{"code":-32603,"message":"error"}}
                            """).isJsonRpcError())
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void rejectsNullError() {
        assertThatThrownBy(() -> assertThatJsonRpcResponse("""
                            {"jsonrpc":"2.0","id":1,"error":null}
                            """).isJsonRpcError())
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void hasContentRejectsEmptyArray() {
        assertThatThrownBy(() -> assertThatJsonRpcResponse("""
                            {"jsonrpc":"2.0","id":1,"result":{"content":[]}}
                            """).isSuccess().hasContent())
                .isInstanceOf(AssertionError.class);
    }
}
