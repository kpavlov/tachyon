/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static dev.tachyonmcp.server.domain.PromptMessage.of;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.prompts.PromptResult;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostStartRegistrationTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startEmptyServer();
    }

    @Test
    void shouldRegisterResourceAfterStart() throws Exception {
        server.resources()
                .register(
                        ResourceDescriptor.of(
                                "post-start-res", "test://post-start", "Post-start resource", "text/plain"),
                        (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(rawUri, "text/plain", ""));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/list"}
                """);
            assertThatJson(response.body()).inPath("$.result.resources[0].name").isEqualTo("post-start-res");
            assertThatJson(response.body()).inPath("$.result.resources[0].uri").isEqualTo("test://post-start");
        }
    }

    @Test
    void shouldRegisterPromptAfterStart() throws Exception {
        server.prompts()
                .register(
                        PromptDescriptor.of("post-start-prompt", "Post-start prompt"),
                        (ctx, request) ->
                                PromptResult.messages(List.of(of(Role.USER, TextContent.of("Hello from post-start")))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/list"}
                """);
            assertThatJson(response.body()).inPath("$.result.prompts[0].name").isEqualTo("post-start-prompt");
            assertThatJson(response.body())
                    .inPath("$.result.prompts[0].description")
                    .isEqualTo("Post-start prompt");
        }
    }

    @Test
    void shouldRegisterResourceWithHandlerAfterStart() throws Exception {
        server.resources()
                .register(
                        ResourceDescriptor.of("handled-res", "test://handled", "Handled resource", "text/plain"),
                        (ctx, rawUri, params, uriTemplate) ->
                                TextResourceContents.of(rawUri, "text/plain", "post-start data", null));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"test://handled"}}
                """);
            assertThatJson(response.body()).inPath("$.result.contents[0].text").isEqualTo("post-start data");
        }
    }
}
