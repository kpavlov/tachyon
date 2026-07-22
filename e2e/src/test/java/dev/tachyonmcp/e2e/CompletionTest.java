/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.features.completions.CompletionResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompletionTest extends AbstractStatelessMcpE2eTest {

    @Test
    void shouldCompletePromptArgument() throws Exception {
        startEmptyServer();
        server.completions()
                .registerForPrompt(
                        "code_review",
                        (ctx, request) -> CompletionResult.of(List.of("python", "pytorch", "pyside"), 10.0, true));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"completion/complete","params":{
                    "ref":{"type":"ref/prompt","name":"code_review"},
                    "argument":{"name":"language","value":"py"}
                }}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.completion.values")
                    .isArray()
                    .containsExactly("python", "pytorch", "pyside");
            assertThatJson(response.body()).inPath("$.result.completion.total").isEqualTo(10.0);
            assertThatJson(response.body())
                    .inPath("$.result.completion.hasMore")
                    .isEqualTo(true);
        }
    }

    @Test
    void shouldCompleteResourceTemplateArgumentUsingContextArguments() throws Exception {
        startEmptyServer();
        server.completions()
                .registerForResource(
                        "file:///{path}",
                        (ctx, request) -> CompletionResult.of(List.of(request.argumentValue() + ".txt")));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"completion/complete","params":{
                    "ref":{"type":"ref/resource","uri":"file:///{path}"},
                    "argument":{"name":"path","value":"note"}
                }}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.completion.values")
                    .isArray()
                    .containsExactly("note.txt");
        }
    }

    @Test
    void shouldReturnEmptyCompletionForUnregisteredRef() throws Exception {
        startEmptyServer();

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"completion/complete","params":{
                    "ref":{"type":"ref/prompt","name":"unknown"},
                    "argument":{"name":"language","value":"py"}
                }}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.completion.values")
                    .isArray()
                    .isEmpty();
        }
    }

    @Test
    void shouldReturnInvalidParamsForMissingRef() throws Exception {
        startEmptyServer();

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"completion/complete","params":{
                    "argument":{"name":"language","value":"py"}
                }}
                """);

            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32602);
        }
    }

    @Test
    void shouldRedactIllegalArgumentExceptionFromInvalidParamsError() throws Exception {
        startEmptyServer();
        server.completions().registerForPrompt("bad-arg", (ctx, request) -> {
            throw new IllegalArgumentException("sensitive internal detail");
        });

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"completion/complete","params":{
                  "ref":{"type":"ref/prompt","name":"bad-arg"},
                  "argument":{"name":"language","value":"java"}
                }}
                """);
            // language=JSON
            var expected = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "error": {"code": -32602, "message": "Invalid params"}
                    }
                    """;
            assertThatJson(response).isEqualTo(expected);
            assertThat(response).doesNotContain("sensitive internal detail");
        }
    }

    @Test
    void shouldAdvertiseCompletionsCapabilityWhenHandlerRegistered() throws Exception {
        startEmptyServer();
        server.completions().registerForPrompt("code_review", (ctx, request) -> CompletionResult.of(List.of()));

        try (var client = createTestClient()) {
            var response = client.post(null, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                        "protocolVersion":"2025-11-25","capabilities":{},
                        "clientInfo":{"name":"test","version":"1.0"}
                    }}
                    """);
            assertThatJson(response.body())
                    .inPath("$.result.capabilities.completions")
                    .isObject();
        }
    }
}
