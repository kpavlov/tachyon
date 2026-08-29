/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.e2e.mcp;

import static dev.tachyonmcp.testkit.JsonRpcResponseAssert.assertThat;
import static dev.tachyonmcp.testkit.McpHttpResponseAssert.assertThatResponse;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.EmbeddedResource;
import dev.tachyonmcp.api.server.domain.ImageContent;
import dev.tachyonmcp.api.server.domain.InvalidArgumentException;
import dev.tachyonmcp.api.server.domain.PromptMessage;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.api.server.features.prompts.PromptResult;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PromptsTest extends AbstractStatelessMcpE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldListRegisteredPrompts() throws Exception {
        startEmptyServer();
        var prompts = server.prompts();
        prompts.register(PromptDescriptor.of("greeting", "A greeting prompt"), List.of(PromptMessage.user("Hello!")));
        prompts.register(PromptDescriptor.of("farewell", "A farewell prompt"), List.of(PromptMessage.user("Goodbye!")));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
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
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"greeting"}}
                """);

            assertThatJson(response.body()).isEqualTo("""
                    {
                      "jsonrpc":"2.0",
                      "id":2,
                      "result":{"description":"A greeting prompt","messages":[{
                        "role":"user",
                        "content":{"type":"text","text":"Hello world!"}
                      }]}
                    }
                    """);
        }
    }

    @Test
    void shouldGetPromptWithArguments() throws Exception {
        startEmptyServer();
        server.prompts()
                .register(
                        PromptDescriptor.of("test_prompt_with_arguments", "A parameterized prompt"),
                        (ctx, request) -> PromptResult.messages(List.of(PromptMessage.user(
                                "Hello, " + request.arguments().json() + "!"))));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
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
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"unknown"}}
                """);

            assertThat(response).isJsonRpcError().hasErrorCode(-32602);
        }
    }

    @Test
    void shouldRedactIllegalArgumentExceptionFromInvalidParamsError() throws Exception {
        startEmptyServer();
        server.prompts().register(prompt -> prompt.name("bad-arg"), (ctx, request) -> {
            throw new IllegalArgumentException("sensitive internal detail");
        });

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"bad-arg"}}
                """);
            assertThatResponse(response)
                    .isJsonRpcError()
                    .hasId(2)
                    .hasErrorCode(-32602)
                    .hasErrorMessage("Invalid params");
            assertThat(response.body()).doesNotContain("sensitive internal detail");
        }
    }

    @Test
    void shouldPreserveInvalidArgumentExceptionDetails() throws Exception {
        startEmptyServer();
        server.prompts().register(prompt -> prompt.name("bad-city"), (ctx, request) -> {
            throw new InvalidArgumentException("city", "unknown city");
        });

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.sendRpc("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"bad-city"}}
                """);
            assertThatResponse(response)
                    .isJsonRpcError()
                    .hasId(2)
                    .hasErrorCode(-32602)
                    .hasErrorMessage("invalid argument 'city': unknown city");
        }
    }

    @Test
    void shouldReturnEmptyListWhenNoPrompts() throws Exception {
        startEmptyServer();

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/list"}
                """);

            assertThatJson(response.body()).isEqualTo("""
                    {"jsonrpc":"2.0","id":2,"result":{"prompts":[]}}
                    """);
        }
    }

    @Test
    void shouldGetPromptWithEmbeddedResource() throws Exception {
        startEmptyServer();
        server.prompts()
                .register(
                        PromptDescriptor.of("embedded", "Prompt with embedded resource"),
                        List.of(PromptMessage.user(EmbeddedResource.of(
                                TextResourceContents.of("test://embedded", "embedded content", "text/plain")))));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"embedded"}}
                """);

            assertThatJson(response.body()).isEqualTo("""
                    {
                      "jsonrpc":"2.0",
                      "id":2,
                      "result":{"description":"Prompt with embedded resource","messages":[{
                        "role":"user",
                        "content":{"type":"resource","resource":{
                          "uri":"test://embedded",
                          "mimeType":"text/plain",
                          "text":"embedded content"
                        }}
                      }]}
                    }
                    """);
        }
    }

    @Test
    void shouldGetPromptWithImage() throws Exception {
        startEmptyServer();
        server.prompts()
                .register(
                        PromptDescriptor.of("image-prompt", "Prompt with image"),
                        List.of(PromptMessage.user(
                                ImageContent.of(Base64.getDecoder().decode("iVBORw0KGgo="), "image/png"))));

        try (var client = createTestClient()) {
            client.initialize();
            var response = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/get","params":{"name":"image-prompt"}}
                """);

            assertThatJson(response.body()).isEqualTo("""
                    {
                      "jsonrpc":"2.0",
                      "id":2,
                      "result":{"description":"Prompt with image","messages":[{
                        "role":"user",
                        "content":{
                          "type":"image",
                          "data":"iVBORw0KGgo=",
                          "mimeType":"image/png"
                        }
                      }]}
                    }
                    """);
        }
    }
}
