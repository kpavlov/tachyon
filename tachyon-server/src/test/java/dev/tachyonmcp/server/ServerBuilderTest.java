/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.domain.UriTemplateValue;
import dev.tachyonmcp.server.features.prompts.PromptResult;
import dev.tachyonmcp.server.features.tools.ToolResult;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    void rejectsBoundedPool() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TachyonServer.builder()
                        .executor(Executors.newFixedThreadPool(1))
                        .build())
                .withMessageContaining("thread per task");
    }

    @Test
    void acceptsVirtualThreadPerTaskExecutor() throws IOException {
        try (var server = TachyonServer.builder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build()) {
            assertThat(server).isNotNull();
        }
    }

    @Test
    void acceptsDefaultExecutor() throws IOException {
        try (var server = TachyonServer.builder().build()) {
            assertThat(server).isNotNull();
        }
    }

    @Test
    void acceptsDescriptorBuilderOverloads() {
        try (var server = TachyonServer.builder()
                .tool(tool -> tool.name("sync-tool"), (ctx, args) -> ToolResult.empty())
                .resource(
                        resource -> resource.name("sync-resource").uri("test://sync"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "text/plain", "sync"))
                .prompt(prompt -> prompt.name("sync-prompt"), List.of(PromptMessage.user("sync")))
                .resourceTemplate(
                        template -> template.name("sync-template").uriTemplate("test://sync/{id}"),
                        (ctx, uri, params) -> TextResourceContents.of(
                                uri, "text/plain", ((UriTemplateValue.Scalar) params.get("id")).value()))
                .build()) {
            assertThat(server.tools().find("sync-tool")).isPresent();
            assertThat(server.resources().find("sync-resource")).isPresent();
            assertThat(((DefaultTachyonServer) server).resolveCapabilities().prompts())
                    .isNotNull();
        }
    }

    @Test
    void acceptsAsyncHandlersWithoutCasts() {
        try (var server = TachyonServer.builder()
                .asyncTool(
                        tool -> tool.name("async-tool"),
                        (ctx, args) -> CompletableFuture.completedFuture(ToolResult.empty()))
                .asyncResource(
                        resource -> resource.name("async-resource").uri("test://async"),
                        (ctx, request) -> CompletableFuture.completedFuture(
                                TextResourceContents.of(request.uri(), "text/plain", "async")))
                .asyncPrompt(
                        prompt -> prompt.name("async-prompt"),
                        (ctx, request) -> CompletableFuture.completedFuture(
                                PromptResult.messages(List.of(PromptMessage.user("async")))))
                .asyncResourceTemplate(
                        template -> template.name("async-template").uriTemplate("test://async/{id}"),
                        (ctx, uri, params) -> CompletableFuture.completedFuture(TextResourceContents.of(
                                uri, "text/plain", ((UriTemplateValue.Scalar) params.get("id")).value())))
                .build()) {
            assertThat(server.tools().find("async-tool")).isPresent();
            assertThat(server.resources().find("async-resource")).isPresent();
            assertThat(((DefaultTachyonServer) server).resolveCapabilities().prompts())
                    .isNotNull();
        }
    }

    @Test
    void resourceRequiresHandler() {
        assertThat(ServerBuilder.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("resource"))
                .hasSize(2)
                .allSatisfy(method -> assertThat(method.getParameterCount()).isEqualTo(2));
    }
}
