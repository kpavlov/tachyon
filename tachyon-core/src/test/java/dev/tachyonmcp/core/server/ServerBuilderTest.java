/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.core.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.domain.UriTemplateValue;
import dev.tachyonmcp.api.server.features.completions.AsyncCompletionFn;
import dev.tachyonmcp.api.server.features.completions.CompletionFn;
import dev.tachyonmcp.api.server.features.completions.CompletionResult;
import dev.tachyonmcp.api.server.features.prompts.PromptResult;
import dev.tachyonmcp.api.server.features.tools.ToolResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ServerBuilder} enforces the thread-per-task executor contract required by
 * blocking-first dispatch.
 *
 * @author Konstantin Pavlov
 */
class ServerBuilderTest {

    @Test
    void exposesBuilderAsInterface() {
        assertThat(TachyonServer.builder()).isInstanceOf(DefaultServerBuilder.class);
    }

    @Test
    void rejectsBoundedPool() {
        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TachyonServer.builder().executor(executor).build())
                    .withMessageContaining("thread per task");
        }
    }

    @Test
    void acceptsVirtualThreadPerTaskExecutor() {
        try (var server = TachyonServer.builder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build()) {
            assertThat(server).isNotNull();
        }
    }

    @Test
    void acceptsDefaultExecutor() {
        try (var server = TachyonServer.builder().build()) {
            assertThat(server).isNotNull();
        }
    }

    @Test
    void registersFeaturesThroughFacades() {
        try (var server = TachyonServer.builder().build()) {
            server.tools().register(tool -> tool.name("sync-tool"), (ctx, request) -> ToolResult.empty());
            server.resources()
                    .register(
                            resource -> resource.name("sync-resource").uri("test://sync"),
                            (ctx, request) -> TextResourceContents.of(request.uri(), "sync", "text/plain"))
                    .registerTemplate(
                            template -> template.name("sync-template").uriTemplate("test://sync/{id}"),
                            (ctx, request) -> TextResourceContents.of(
                                    request.uri(),
                                    ((UriTemplateValue.Scalar) request.params().get("id")).value(),
                                    "text/plain"));
            server.prompts().register(prompt -> prompt.name("sync-prompt"), List.of(PromptMessage.user("sync")));

            assertThat(server.tools().find("sync-tool")).isPresent();
            assertThat(server.resources().find("sync-resource")).isPresent();
            assertThat(server.resources().findTemplate("sync-template")).isPresent();
            assertThat(server.prompts().find("sync-prompt")).isPresent();
        }
    }

    @Test
    void acceptsAsyncHandlersWithoutCasts() {
        try (var server = TachyonServer.builder().build()) {
            server.tools()
                    .registerAsync(
                            tool -> tool.name("async-tool"),
                            (ctx, request) -> CompletableFuture.completedFuture(ToolResult.empty()));
            server.resources()
                    .registerAsync(
                            resource -> resource.name("async-resource").uri("test://async"),
                            (ctx, request) -> CompletableFuture.completedFuture(
                                    TextResourceContents.of(request.uri(), "async", "text/plain")))
                    .registerTemplateAsync(
                            template -> template.name("async-template").uriTemplate("test://async/{id}"),
                            (ctx, request) -> CompletableFuture.completedFuture(TextResourceContents.of(
                                    request.uri(),
                                    ((UriTemplateValue.Scalar) request.params().get("id")).value(),
                                    "text/plain")));
            server.prompts()
                    .registerAsync(
                            prompt -> prompt.name("async-prompt"),
                            (ctx, request) -> CompletableFuture.completedFuture(
                                    PromptResult.messages(List.of(PromptMessage.user("async")))));

            assertThat(server.tools().find("async-tool")).isPresent();
            assertThat(server.resources().find("async-resource")).isPresent();
            assertThat(server.resources().findTemplate("async-template")).isPresent();
            assertThat(server.prompts().find("async-prompt")).isPresent();
        }
    }

    @Test
    void registersBootstrapFeaturesThroughFacades() {
        try (var server = TachyonServer.builder()
                .withTools(tools ->
                        tools.register(tool -> tool.name("bootstrap-tool"), (ctx, request) -> ToolResult.empty()))
                .withResources(resources -> resources.register(
                        resource -> resource.name("bootstrap-resource").uri("test://bootstrap"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "bootstrap", "text/plain")))
                .withPrompts(prompts -> prompts.register(
                        prompt -> prompt.name("bootstrap-prompt"), List.of(PromptMessage.user("bootstrap"))))
                .withCompletions(completions -> completions.registerForPrompt(
                        "bootstrap-prompt", (ctx, request) -> CompletionResult.of(List.of("bootstrap"))))
                .build()) {
            assertThat(server.tools().find("bootstrap-tool")).isPresent();
            assertThat(server.resources().find("bootstrap-resource")).isPresent();
            assertThat(server.prompts().find("bootstrap-prompt")).isPresent();
            assertThat(server.completions().unregisterForPrompt("bootstrap-prompt"))
                    .isTrue();
        }
    }

    @Test
    void retainsCompletionRegistrationForPlainResource() {
        CompletionFn handler = (ctx, request) -> CompletionResult.of(List.of("sync-completion"));
        try (var server = TachyonServer.builder().build()) {
            server.resources()
                    .register(
                            resource -> resource.name("sync-resource").uri("test://sync-completed"),
                            (ctx, request) -> TextResourceContents.of(request.uri(), "text", "text/plain"));
            server.completions().registerForResource("test://sync-completed", handler);

            assertThat(server.completions().unregisterForResource("test://sync-completed"))
                    .isTrue();
        }
    }

    @Test
    void retainsAsyncCompletionRegistrations() {
        AsyncCompletionFn promptHandler =
                (ctx, request) -> CompletableFuture.completedFuture(CompletionResult.of(List.of("prompt-completion")));
        AsyncCompletionFn resourceHandler = (ctx, request) ->
                CompletableFuture.completedFuture(CompletionResult.of(List.of("resource-completion")));
        try (var server = TachyonServer.builder().build()) {
            server.prompts()
                    .register(prompt -> prompt.name("async-completed-prompt"), List.of(PromptMessage.user("prompt")));
            server.resources()
                    .register(
                            resource -> resource.name("async-resource").uri("test://async-completed"),
                            (ctx, request) -> TextResourceContents.of(request.uri(), "text", "text/plain"));
            server.completions()
                    .registerForPromptAsync("async-completed-prompt", promptHandler)
                    .registerForResourceAsync("test://async-completed", resourceHandler);

            assertThat(server.completions().unregisterForPrompt("async-completed-prompt"))
                    .isTrue();
            assertThat(server.completions().unregisterForResource("test://async-completed"))
                    .isTrue();
        }
    }

    @Test
    void builderHasNoFeatureRegistrationMethods() {
        assertThat(ServerBuilder.class.getDeclaredMethods())
                .extracting(method -> method.getName())
                .doesNotContain(
                        "tool",
                        "asyncTool",
                        "resource",
                        "asyncResource",
                        "resourceTemplate",
                        "asyncResourceTemplate",
                        "prompt",
                        "asyncPrompt",
                        "promptCompletion",
                        "asyncPromptCompletion",
                        "resourceCompletion",
                        "asyncResourceCompletion");
    }

    @Test
    void builderHasNoStartMethod() {
        assertThat(ServerBuilder.class.getDeclaredMethods())
                .extracting(method -> method.getName())
                .doesNotContain("start");
    }

    @Test
    void portThrowsBeforeStart() {
        try (var server = TachyonServer.builder().build()) {
            assertThatIllegalStateException().isThrownBy(server::port);
        }
    }
}
