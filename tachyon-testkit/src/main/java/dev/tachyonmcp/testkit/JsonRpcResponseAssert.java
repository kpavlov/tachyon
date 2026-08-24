/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.testkit;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.net.http.HttpResponse;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.intellij.lang.annotations.Language;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fluent, branch-safe assertions over a JSON-RPC response envelope. */
public final class JsonRpcResponseAssert extends AbstractAssert<JsonRpcResponseAssert, JsonNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonRpcResponseAssert(JsonNode envelope) {
        super(envelope, JsonRpcResponseAssert.class);
    }

    /** Creates assertions for a parsed JSON-RPC response envelope. */
    public static JsonRpcResponseAssert assertThat(JsonNode envelope) {
        return new JsonRpcResponseAssert(envelope);
    }

    /** Creates assertions for an HTTP response containing a JSON-RPC envelope. */
    public static JsonRpcResponseAssert assertThat(HttpResponse<String> response) {
        return assertThatJsonRpcResponse(response.body());
    }

    /** Creates assertions for a raw JSON-RPC response body. */
    public static JsonRpcResponseAssert assertThatJsonRpcResponse(String json) {
        return new JsonRpcResponseAssert(MAPPER.readTree(json));
    }

    /** Verifies the success branch and returns success-only assertions. */
    public JsonRpcSuccessAssert isSuccess() {
        isNotNull();
        if (actual.has("error") || !actual.has("result")) {
            failWithMessage("Expected a successful JSON-RPC response but was: %s", actual);
        }
        return new JsonRpcSuccessAssert(actual);
    }

    /** Verifies the error branch and returns error-only assertions. */
    public JsonRpcErrorAssert isJsonRpcError() {
        isNotNull();
        if (actual.has("result") || !actual.path("error").isObject()) {
            failWithMessage("Expected a JSON-RPC error response but was: %s", actual);
        }
        return new JsonRpcErrorAssert(actual);
    }

    /** Assertions available only after verifying a successful response. */
    public static final class JsonRpcSuccessAssert extends AbstractAssert<JsonRpcSuccessAssert, JsonNode> {

        private JsonRpcSuccessAssert(JsonNode envelope) {
            super(envelope, JsonRpcSuccessAssert.class);
        }

        /** Verifies the JSON-RPC response id. */
        public JsonRpcSuccessAssert hasId(Object expected) {
            var expectedId = MAPPER.valueToTree(expected);
            var id = actual.path("id");
            if (!id.equals(expectedId)) {
                failWithMessage("Expected JSON-RPC id <%s> but was <%s> in: %s", expectedId, id, actual);
            }
            return this;
        }

        /** Verifies a successful tool result reports {@code isError: true}. */
        public JsonRpcSuccessAssert isToolError() {
            var isError = actual.path("result").path("isError");
            if (!isError.isBoolean() || !isError.asBoolean()) {
                failWithMessage("Expected tool result 'isError' to be true in: %s", actual);
            }
            return this;
        }

        /** Verifies result content contains an equal text block. */
        public JsonRpcSuccessAssert hasTextContent(String expectedText) {
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

        /** Verifies result content contains a text block. */
        public JsonRpcSuccessAssert hasTextContent() {
            var content = actual.path("result").path("content");
            for (var block : content) {
                if ("text".equals(block.path("type").asString())
                        && block.path("text").isString()) {
                    return this;
                }
            }
            failWithMessage("Expected result content to contain a text block but was: %s", content);
            return this;
        }

        /**
         * Verifies result content is a non-empty array.
         */
        public JsonRpcSuccessAssert hasContent() {
            var content = actual.path("result").path("content");
            if (!content.isArray() || content.isEmpty()) {
                failWithMessage("Expected result 'content' to be a non-empty array in: %s", actual);
            }
            return this;
        }

        /**
         * Verifies result content contains exactly the expected blocks, in order.
         */
        public JsonRpcSuccessAssert hasContentExactly(JsonNode... expected) {
            var content = actual.path("result").path("content");
            var expectedContent = MAPPER.valueToTree(expected);
            if (!content.equals(expectedContent)) {
                failWithMessage("Expected result content <%s> but was <%s> in: %s", expectedContent, content, actual);
            }
            return this;
        }

        /**
         * Verifies the complete JSON-RPC result value.
         */
        public JsonRpcSuccessAssert hasResult(JsonNode expected) {
            var result = actual.path("result");
            if (!result.equals(expected)) {
                failWithMessage("Expected result <%s> but was <%s> in: %s", expected, result, actual);
            }
            return this;
        }

        /**
         * Verifies the MCP result type.
         */
        public JsonRpcSuccessAssert hasResultType(String expected) {
            var resultType = actual.path("result").path("resultType").asString(null);
            if (!expected.equals(resultType)) {
                failWithMessage("Expected resultType <%s> but was <%s> in: %s", expected, resultType, actual);
            }
            return this;
        }

        /** Verifies {@code structuredContent} equals the expected JSON. */
        public JsonRpcSuccessAssert hasStructuredContent(JsonNode expected) {
            var structuredContent = actual.path("result").path("structuredContent");
            if (!structuredContent.equals(expected)) {
                failWithMessage(
                        "Expected structuredContent <%s> but was <%s> in: %s", expected, structuredContent, actual);
            }
            return this;
        }

        /**
         * Verifies {@code structuredContent} equals the expected JSON text.
         */
        public JsonRpcSuccessAssert hasStructuredContent(@Language("json") String expectedJson) {
            assertThatJson(actual.path("result").path("structuredContent")).isEqualTo(expectedJson);
            return this;
        }
    }

    /**
     * Assertions available only after verifying an error response.
     */
    public static final class JsonRpcErrorAssert extends AbstractAssert<JsonRpcErrorAssert, JsonNode> {

        private JsonRpcErrorAssert(JsonNode envelope) {
            super(envelope, JsonRpcErrorAssert.class);
        }

        /** Verifies the JSON-RPC response id. */
        public JsonRpcErrorAssert hasId(Object expected) {
            var expectedId = MAPPER.valueToTree(expected);
            var id = actual.path("id");
            if (!id.equals(expectedId)) {
                failWithMessage("Expected JSON-RPC id <%s> but was <%s> in: %s", expectedId, id, actual);
            }
            return this;
        }

        /** Verifies the JSON-RPC error code. */
        public JsonRpcErrorAssert hasErrorCode(int expectedCode) {
            var code = actual.path("error").path("code");
            if (!code.isInt() || code.asInt() != expectedCode) {
                failWithMessage("Expected JSON-RPC error code <%s> but was <%s> in: %s", expectedCode, code, actual);
            }
            return this;
        }

        /** Verifies the exact JSON-RPC error message. */
        public JsonRpcErrorAssert hasErrorMessage(String expectedMessage) {
            var message = actual.path("error").path("message").asString(null);
            if (!expectedMessage.equals(message)) {
                failWithMessage(
                        "Expected JSON-RPC error message <%s> but was <%s> in: %s", expectedMessage, message, actual);
            }
            return this;
        }

        /** Verifies the JSON-RPC error message contains the expected text. */
        public JsonRpcErrorAssert hasErrorMessageContaining(String expectedText) {
            var message = actual.path("error").path("message").asString("");
            if (!message.contains(expectedText)) {
                failWithMessage(
                        "Expected JSON-RPC error message containing <%s> but was <%s> in: %s",
                        expectedText, message, actual);
            }
            return this;
        }

        /** Runs an assertion against {@code error.data}. */
        public JsonRpcErrorAssert hasErrorDataSatisfying(Consumer<JsonNode> assertion) {
            assertion.accept(actual.path("error").path("data"));
            return this;
        }

        /**
         * Verifies the complete JSON-RPC error value.
         */
        public JsonRpcErrorAssert hasError(JsonNode expected) {
            var error = actual.path("error");
            if (!error.equals(expected)) {
                failWithMessage("Expected error <%s> but was <%s> in: %s", expected, error, actual);
            }
            return this;
        }
    }
}
