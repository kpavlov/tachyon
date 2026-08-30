/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThatJsonRpcResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
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

    static Stream<Arguments> hasResultMatches() {
        return Stream.of(
                Arguments.of("object", """
                                {"jsonrpc":"2.0","id":1,"result":{"content":[]}}
                                """, JSON.readTree("{\"content\":[]}")),
                Arguments.of("array", """
                                {"jsonrpc":"2.0","id":1,"result":[1,2,3]}
                                """, JSON.readTree("[1,2,3]")),
                Arguments.of("string", """
                                {"jsonrpc":"2.0","id":1,"result":"hello"}
                                """, JSON.readTree("\"hello\"")),
                Arguments.of("number", """
                                {"jsonrpc":"2.0","id":1,"result":42}
                                """, JSON.readTree("42")),
                Arguments.of("boolean", """
                                {"jsonrpc":"2.0","id":1,"result":true}
                                """, JSON.readTree("true")),
                Arguments.of("null", """
                                {"jsonrpc":"2.0","id":1,"result":null}
                                """, JSON.readTree("null")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hasResultMatches")
    void hasResultMatches(String name, String envelope, JsonNode expected) {
        assertThatJsonRpcResponse(envelope).isSuccess().hasResult(expected);
    }

    static Stream<Arguments> hasResultRejects() {
        return Stream.of(
                Arguments.of("object mismatch", """
                                {"jsonrpc":"2.0","id":1,"result":{"content":[]}}
                                """, JSON.readTree("{\"foo\":\"bar\"}")),
                Arguments.of("array mismatch", """
                                {"jsonrpc":"2.0","id":1,"result":[1,2,3]}
                                """, JSON.readTree("[1,2]")),
                Arguments.of("string mismatch", """
                                {"jsonrpc":"2.0","id":1,"result":"hello"}
                                """, JSON.readTree("\"world\"")),
                Arguments.of("number mismatch", """
                                {"jsonrpc":"2.0","id":1,"result":42}
                                """, JSON.readTree("99")),
                Arguments.of("boolean mismatch", """
                                {"jsonrpc":"2.0","id":1,"result":true}
                                """, JSON.readTree("false")),
                Arguments.of("null mismatch", """
                                {"jsonrpc":"2.0","id":1,"result":null}
                                """, JSON.readTree("0")),
                Arguments.of("type mismatch object vs array", """
                                {"jsonrpc":"2.0","id":1,"result":{}}
                                """, JSON.readTree("[]")),
                Arguments.of("type mismatch string vs number", """
                                {"jsonrpc":"2.0","id":1,"result":"1"}
                                """, JSON.readTree("1")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hasResultRejects")
    void hasResultRejects(String name, String envelope, JsonNode wrongExpected) {
        assertThatThrownBy(() -> assertThatJsonRpcResponse(envelope).isSuccess().hasResult(wrongExpected))
                .isInstanceOf(AssertionError.class);
    }
}
