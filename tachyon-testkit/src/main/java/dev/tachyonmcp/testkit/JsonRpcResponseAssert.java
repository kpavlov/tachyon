/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import java.net.http.HttpResponse;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fluent AssertJ assertions over a JSON-RPC response envelope ({@code {jsonrpc, id, result|error}}),
 * as produced by {@link McpClient#post} / {@link McpClient#sendRpc}.
 *
 * <pre>{@code
 * var response = client.post("""
 *     {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}
 *     """);
 *
 * assertThat(response).hasTextContent("echo:hi");
 * }</pre>
 */
public final class JsonRpcResponseAssert extends AbstractAssert<JsonRpcResponseAssert, JsonNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonRpcResponseAssert(JsonNode envelope) {
        super(envelope, JsonRpcResponseAssert.class);
    }

    /**
     * Creates assertions for an already-parsed JSON-RPC response envelope.
     *
     * @param envelope the JSON-RPC response envelope
     * @return a new assertion object
     */
    public static JsonRpcResponseAssert assertThat(JsonNode envelope) {
        return new JsonRpcResponseAssert(envelope);
    }

    /**
     * Creates assertions for an HTTP response carrying a JSON-RPC response envelope as its body.
     *
     * @param httpResponse the HTTP response
     * @return a new assertion object
     */
    public static JsonRpcResponseAssert assertThat(HttpResponse<String> httpResponse) {
        return assertThatJsonRpcResponse(httpResponse.body());
    }

    /**
     * Creates assertions for a raw JSON-RPC response body, e.g. as returned by {@link
     * McpClient#sendRpc}. Named distinctly from {@code assertThat} to avoid an ambiguous overload
     * against {@code org.assertj.core.api.Assertions.assertThat(String)} when both are statically
     * imported.
     *
     * @param json the JSON-RPC response body
     * @return a new assertion object
     */
    public static JsonRpcResponseAssert assertThatJsonRpcResponse(String json) {
        return new JsonRpcResponseAssert(MAPPER.readTree(json));
    }

    /**
     * Asserts the envelope carries a {@code result} and no {@code error}.
     *
     * @return {@code this}
     */
    public JsonRpcResponseAssert isSuccess() {
        isNotNull();
        if (!actual.path("error").isMissingNode()) {
            failWithMessage("Expected a successful JSON-RPC response but found an error: %s", actual.path("error"));
        }
        if (actual.path("result").isMissingNode()) {
            failWithMessage(
                    "Expected a successful JSON-RPC response with a 'result' but found neither 'result' nor"
                            + " 'error': %s",
                    actual);
        }
        return this;
    }

    /**
     * Asserts the envelope carries an {@code error}.
     *
     * @return {@code this}
     */
    public JsonRpcResponseAssert isJsonRpcError() {
        isNotNull();
        if (actual.path("error").isMissingNode()) {
            failWithMessage("Expected a JSON-RPC error response but 'error' is missing: %s", actual);
        }
        return this;
    }

    /**
     * Asserts the JSON-RPC error code equals {@code expectedCode}.
     *
     * @param expectedCode the expected {@code error.code}
     * @return {@code this}
     */
    public JsonRpcResponseAssert hasErrorCode(int expectedCode) {
        isJsonRpcError();
        var actualCode = actual.path("error").path("code").asInt();
        if (actualCode != expectedCode) {
            failWithMessage("Expected JSON-RPC error code <%s> but was <%s> in: %s", expectedCode, actualCode, actual);
        }
        return this;
    }

    /**
     * Asserts the JSON-RPC error message equals {@code expectedMessage} exactly.
     *
     * @param expectedMessage the expected {@code error.message}
     * @return {@code this}
     */
    public JsonRpcResponseAssert hasErrorMessage(String expectedMessage) {
        isJsonRpcError();
        var actualMessage = actual.path("error").path("message").asString(null);
        if (!expectedMessage.equals(actualMessage)) {
            failWithMessage(
                    "Expected JSON-RPC error message <%s> but was <%s> in: %s", expectedMessage, actualMessage, actual);
        }
        return this;
    }

    /**
     * Asserts the JSON-RPC error message contains {@code substring}.
     *
     * @param substring the expected substring of {@code error.message}
     * @return {@code this}
     */
    public JsonRpcResponseAssert hasErrorMessageContaining(String substring) {
        isJsonRpcError();
        var actualMessage = actual.path("error").path("message").asString("");
        if (!actualMessage.contains(substring)) {
            failWithMessage(
                    "Expected JSON-RPC error message containing <%s> but was <%s> in: %s",
                    substring, actualMessage, actual);
        }
        return this;
    }

    /**
     * Runs {@code assertion} against the JSON-RPC error's {@code data} field.
     *
     * @param assertion the assertion to run against {@code error.data}
     * @return {@code this}
     */
    public JsonRpcResponseAssert hasErrorDataSatisfying(Consumer<JsonNode> assertion) {
        isJsonRpcError();
        assertion.accept(actual.path("error").path("data"));
        return this;
    }

    /**
     * Asserts a successful tool call result has {@code isError: true} — the MCP tool-execution
     * failure channel, distinct from a JSON-RPC-level error.
     *
     * @return {@code this}
     */
    public JsonRpcResponseAssert isToolError() {
        isSuccess();
        var isError = actual.path("result").path("isError");
        if (!(isError.isBoolean() && isError.asBoolean())) {
            failWithMessage("Expected tool result 'isError' to be true in: %s", actual);
        }
        return this;
    }

    /**
     * Asserts the result's content blocks include a text block equal to {@code expectedText}.
     *
     * @param expectedText the expected text content
     * @return {@code this}
     */
    public JsonRpcResponseAssert hasTextContent(String expectedText) {
        isSuccess();
        var content = actual.path("result").path("content");
        for (var block : content) {
            if ("text".equals(block.path("type").asString())
                    && expectedText.equals(block.path("text").asString())) {
                return this;
            }
        }
        failWithMessage("Expected result content to contain text <%s> but was: %s", expectedText, content);
        return this;
    }

    /**
     * Asserts the result's {@code content} field is present and is an array.
     *
     * @return {@code this}
     */
    public JsonRpcResponseAssert hasContent() {
        isSuccess();
        if (!actual.path("result").path("content").isArray()) {
            failWithMessage("Expected result 'content' to be an array in: %s", actual);
        }
        return this;
    }

    /**
     * Asserts the result's {@code structuredContent} equals {@code expected}.
     *
     * @param expected the expected structured content
     * @return {@code this}
     */
    public JsonRpcResponseAssert hasStructuredContent(JsonNode expected) {
        isSuccess();
        var actualStructured = actual.path("result").path("structuredContent");
        if (!actualStructured.equals(expected)) {
            failWithMessage("Expected structuredContent <%s> but was <%s> in: %s", expected, actualStructured, actual);
        }
        return this;
    }
}
