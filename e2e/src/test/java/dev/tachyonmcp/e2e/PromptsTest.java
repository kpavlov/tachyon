/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.EmbeddedResource;
import dev.tachyonmcp.server.domain.ImageContent;
import dev.tachyonmcp.server.domain.PromptMessage;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PromptsTest extends AbstractMcpE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldListRegisteredPrompts() throws Exception {
        startEmptyServer();
        var prompts = server.prompts();
        prompts.register(PromptDescriptor.of("greeting", "A greeting prompt"), List.of(PromptMessage.user("Hello!")));
        prompts.register(PromptDescriptor.of("farewell", "A farewell prompt"), List.of(PromptMessage.user("Goodbye!")));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/list"}
                """);

            var root = MAPPER.readTree(response.body());
            var resultPrompts = root.at("/result/prompts");
            assertThat(resultPrompts).isNotNull();
            assertThat(resultPrompts.size()).isEqualTo(2);
            assertThat(resultPrompts.get(0).get("name").asString()).isIn("farewell", "greeting");
            assertThat(resultPrompts.get(1).get("name").asString()).isIn("farewell", "greeting");
        }
    }

    @Test
    void shouldGetSimplePrompt() throws Exception {
        startEmptyServer();
        server.prompts()
                .register(
                        PromptDescriptor.of("greeting", "A greeting prompt"),
                        List.of(PromptMessage.user("Hello world!")));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"greeting"}}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.messages[0].content.text")
                    .isEqualTo("Hello world!");
            assertThatJson(response.body()).inPath("$.result.messages[0].role").isEqualTo("user");
        }
    }

    @Test
    void shouldGetPromptWithArguments() throws Exception {
        startEmptyServer();
        server.prompts()
                .register(
                        PromptDescriptor.of("test_prompt_with_arguments", "A parameterized prompt"),
                        (ctx, request) -> PromptResult.messages(
                                List.of(PromptMessage.user("Hello, " + request.arguments() + "!"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"test_prompt_with_arguments","arguments":{"name":"World"}}}
                """);

            var text = MAPPER.readTree(response.body())
                    .at("/result/messages/0/content/text")
                    .asString();
            assertThat(text).isEqualTo("Hello, {\"name\":\"World\"}!");
        }
    }

    @Test
    void shouldReturnErrorForUnknownPrompt() throws Exception {
        startEmptyServer();

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"unknown"}}
                """);

            assertThatJson(response.body()).inPath("$.error.code").isEqualTo(-32600);
        }
    }

    @Test
    void shouldReturnEmptyListWhenNoPrompts() throws Exception {
        startEmptyServer();

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/list"}
                """);

            assertThatJson(response.body()).inPath("$.result.prompts").isArray().isEmpty();
        }
    }

    @Test
    void shouldGetPromptWithEmbeddedResource() throws Exception {
        startEmptyServer();
        server.prompts()
                .register(
                        PromptDescriptor.of("embedded", "Prompt with embedded resource"),
                        List.of(PromptMessage.user(EmbeddedResource.of(
                                TextResourceContents.of("test://embedded", "text/plain", "embedded content")))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"embedded"}}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.messages[0].content.type")
                    .isEqualTo("resource");
            assertThatJson(response.body())
                    .inPath("$.result.messages[0].content.resource.text")
                    .isEqualTo("embedded content");
        }
    }

    @Test
    void shouldGetPromptWithImage() throws Exception {
        startEmptyServer();
        server.prompts()
                .register(
                        PromptDescriptor.of("image-prompt", "Prompt with image"),
                        List.of(PromptMessage.user(ImageContent.of("iVBORw0KGgo=", "image/png"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.post(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"image-prompt"}}
                """);

            assertThatJson(response.body())
                    .inPath("$.result.messages[0].content.type")
                    .isEqualTo("image");
            assertThatJson(response.body())
                    .inPath("$.result.messages[0].content.data")
                    .isEqualTo("iVBORw0KGgo=");
            assertThatJson(response.body())
                    .inPath("$.result.messages[0].content.mimeType")
                    .isEqualTo("image/png");
        }
    }
}
