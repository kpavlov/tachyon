/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.features.completions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.protocol.mcp.v2025_11_25.models.CompleteResult;
import dev.tachyonmcp.server.RpcMethodHandler;
import dev.tachyonmcp.server.session.DefaultDispatchContext;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcError;
import dev.tachyonmcp.transport.jsonrpc.JsonRpcErrors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompletionRegistryTest {

    private final DefaultCompletionRegistry registry = new DefaultCompletionRegistry();
    private final HashMap<String, RpcMethodHandler> handlers = new HashMap<>();

    @BeforeEach
    void setUp() {
        registry.registerHandlers(handlers);
    }

    private static Object complete(HashMap<String, RpcMethodHandler> handlers, Object params) throws Exception {
        return handlers.get("completion/complete").handle(DefaultDispatchContext.noop(), params);
    }

    @Test
    void interfaceDefaultOverloadsRegisterAndLookUp() {
        CompletionRegistry api = registry;

        api.registerForPrompt("greeting", (ctx, request) -> CompletionResult.of(List.of("sync")))
                .registerForPromptAsync(
                        "async-prompt",
                        (ctx, request) -> CompletableFuture.completedFuture(CompletionResult.of(List.of("async"))));
        api.registerForResource("file:///{path}", (ctx, request) -> CompletionResult.of(List.of("resource")))
                .registerForResourceAsync(
                        "file:///async/{path}",
                        (ctx, request) ->
                                CompletableFuture.completedFuture(CompletionResult.of(List.of("resource-async"))));

        assertThat(api.findForPrompt("greeting")).isPresent();
        assertThat(api.findForPrompt("async-prompt")).isPresent();
        assertThat(api.findForPrompt("missing")).isEmpty();
        assertThat(api.findForResource("file:///{path}")).isPresent();
        assertThat(api.findForResource("file:///async/{path}")).isPresent();
        assertThat(api.findForResource("missing")).isEmpty();

        assertThat(api.unregisterForPrompt("greeting")).isTrue();
        assertThat(api.unregisterForPrompt("greeting")).isFalse();
        assertThat(api.findForPrompt("greeting")).isEmpty();

        assertThat(api.unregisterForResource("file:///{path}")).isTrue();
        assertThat(api.unregisterForResource("file:///{path}")).isFalse();
        assertThat(api.findForResource("file:///{path}")).isEmpty();
    }

    @Test
    void isEmptyReflectsBothNamespaces() {
        assertThat(registry.isEmpty()).isTrue();

        registry.registerForPrompt("greeting", (ctx, request) -> CompletionResult.of(List.of()));
        assertThat(registry.isEmpty()).isFalse();

        registry.unregisterForPrompt("greeting");
        registry.registerForResource("file:///{path}", (ctx, request) -> CompletionResult.of(List.of()));
        assertThat(registry.isEmpty()).isFalse();
    }

    @Test
    void completesPromptArgumentUsingRegisteredHandler() throws Exception {
        registry.registerForPrompt(
                "code_review",
                (ctx, request) -> CompletionResult.of(List.of("python", "pytorch", "pyside"), 10.0, true));

        var result = complete(
                handlers,
                Map.of(
                        "ref", Map.of("type", "ref/prompt", "name", "code_review"),
                        "argument", Map.of("name", "language", "value", "py")));

        assertThat(result).isInstanceOf(CompleteResult.class);
        var completion = ((CompleteResult) result).completion();
        assertThat(completion.values()).containsExactly("python", "pytorch", "pyside");
        assertThat(completion.total()).isEqualTo(10.0);
        assertThat(completion.hasMore()).isTrue();
    }

    @Test
    void completesResourceArgumentUsingRegisteredHandler() throws Exception {
        registry.registerForResource("file:///{path}", (ctx, request) -> CompletionResult.of(List.of("a.txt")));

        var result = complete(
                handlers,
                Map.of(
                        "ref", Map.of("type", "ref/resource", "uri", "file:///{path}"),
                        "argument", Map.of("name", "path", "value", "a")));

        assertThat(result).isInstanceOf(CompleteResult.class);
        assertThat(((CompleteResult) result).completion().values()).containsExactly("a.txt");
    }

    @Test
    void passesResolvedArgumentsFromContext() throws Exception {
        registry.registerForPrompt(
                "code_review",
                (ctx, request) ->
                        CompletionResult.of(List.of(request.resolvedArguments().get("language") + "-flask")));

        var result = complete(
                handlers,
                Map.of(
                        "ref", Map.of("type", "ref/prompt", "name", "code_review"),
                        "argument", Map.of("name", "framework", "value", "fla"),
                        "context", Map.of("arguments", Map.of("language", "python"))));

        assertThat(((CompleteResult) result).completion().values()).containsExactly("python-flask");
    }

    @Test
    void returnsEmptyCompletionWhenNoHandlerRegisteredForRef() throws Exception {
        var result = complete(
                handlers,
                Map.of(
                        "ref", Map.of("type", "ref/prompt", "name", "unknown"),
                        "argument", Map.of("name", "language", "value", "py")));

        assertThat(result).isInstanceOf(CompleteResult.class);
        var completion = ((CompleteResult) result).completion();
        assertThat(completion.values()).isEmpty();
        assertThat(completion.hasMore()).isFalse();
    }

    @Test
    void truncatesToMaxValuesAndForcesHasMore() throws Exception {
        var values =
                java.util.stream.IntStream.range(0, 150).mapToObj(i -> "v" + i).toList();
        registry.registerForPrompt("many", (ctx, request) -> CompletionResult.of(values));

        var result = complete(
                handlers,
                Map.of(
                        "ref", Map.of("type", "ref/prompt", "name", "many"),
                        "argument", Map.of("name", "arg", "value", "")));

        var completion = ((CompleteResult) result).completion();
        assertThat(completion.values()).hasSize(100);
        assertThat(completion.hasMore()).isTrue();
    }

    @Test
    void returnsInvalidParamsWhenRefMissing() throws Exception {
        var result = complete(handlers, Map.of("argument", Map.of("name", "a", "value", "b")));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INVALID_PARAMS);
    }

    @Test
    void returnsInvalidParamsWhenArgumentMissing() throws Exception {
        var result = complete(handlers, Map.of("ref", Map.of("type", "ref/prompt", "name", "code_review")));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INVALID_PARAMS);
    }

    @Test
    void returnsInvalidParamsForUnknownRefType() throws Exception {
        var result = complete(
                handlers,
                Map.of(
                        "ref", Map.of("type", "ref/unknown", "name", "x"),
                        "argument", Map.of("name", "a", "value", "b")));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INVALID_PARAMS);
    }

    @Test
    void returnsInternalErrorWhenHandlerThrows() throws Exception {
        registry.registerForPrompt("boom", (ctx, request) -> {
            throw new RuntimeException("kaboom");
        });

        var result = complete(
                handlers,
                Map.of(
                        "ref", Map.of("type", "ref/prompt", "name", "boom"),
                        "argument", Map.of("name", "a", "value", "b")));

        assertThat(result).isInstanceOf(JsonRpcError.class);
        assertThat(((JsonRpcError) result).code()).isEqualTo(JsonRpcErrors.INTERNAL_ERROR);
    }
}
