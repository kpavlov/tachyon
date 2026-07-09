/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static dev.tachyonmcp.server.domain.PromptMessage.of;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.PromptArgument;
import dev.tachyonmcp.server.domain.Role;
import dev.tachyonmcp.server.domain.TextContent;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PromptCapabilitiesTest extends AbstractMcpE2eTest {

    @Override
    protected void startDefaultServer() {
        startServer(it -> it.tool(EchoToolHandler.create()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
            greeting | Greeting Title | true
            simple   |                | false
            """)
    void shouldIncludeTitle(String promptName, String title, boolean hasTitle) throws Exception {
        if (hasTitle) {
            server.prompts()
                    .add(
                            PromptDescriptor.of(promptName, "A " + promptName + " prompt", title, null, null),
                            List.of(of(Role.USER, TextContent.of("Hello"))));
        } else {
            server.prompts().add(PromptDescriptor.of(promptName, "A " + promptName + " prompt"), List.of());
        }

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/list"}
                """);

            var mapper = new ObjectMapper();
            var root = mapper.readTree(response.body());
            var prompts = root.at("/result/prompts");
            assertThat(prompts.size()).isGreaterThanOrEqualTo(1);

            var prompt = findByName(prompts, promptName);
            assertThat(prompt).isNotNull();
            if (hasTitle) {
                assertThat(prompt.get("title").asString()).isEqualTo(title);
            } else {
                assertThat(prompt.has("title")).isFalse();
            }
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
            generator | true  | 2 | animal:Type of animal:true | count::false
            simple    | false | 0 | |
            """)
    void shouldIncludeArguments(String promptName, boolean hasArguments, int argCount, String arg0, String arg1)
            throws Exception {
        if (hasArguments) {
            var args = List.of(parseArgument(arg0), parseArgument(arg1));
            server.prompts()
                    .add(
                            PromptDescriptor.of(promptName, "A " + promptName + " prompt", null, args, null),
                            List.of(of(Role.USER, TextContent.of("generate"))));
        } else {
            server.prompts().add(PromptDescriptor.of(promptName, "A " + promptName + " prompt"), List.of());
        }

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/list"}
                """);

            var mapper = new ObjectMapper();
            var root = mapper.readTree(response.body());
            var prompts = root.at("/result/prompts");
            assertThat(prompts.size()).isGreaterThanOrEqualTo(1);

            var prompt = findByName(prompts, promptName);
            assertThat(prompt).isNotNull();
            if (hasArguments) {
                assertThat(prompt.has("arguments")).isTrue();
                var arguments = prompt.get("arguments");
                assertThat(arguments.isArray()).isTrue();
                assertThat(arguments.size()).isEqualTo(argCount);
            } else {
                assertThat(prompt.has("arguments")).isFalse();
            }
        }
    }

    @Test
    void shouldIncludeTitleAndArgumentsOnSamePrompt() throws Exception {
        var args = List.of(PromptArgument.of("name", null, "Your name", null));
        server.prompts()
                .add(
                        PromptDescriptor.of(
                                "personalized", "Personalized greeting", "Personalized Greeting", args, null),
                        List.of(of(Role.USER, TextContent.of("Hello"))));

        try (var client = createTestClient()) {
            var sessionId = client.initialize();
            var response = client.sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"prompts/list"}
                """);

            var mapper = new ObjectMapper();
            var root = mapper.readTree(response.body());
            var prompts = root.at("/result/prompts");

            var p = findByName(prompts, "personalized");
            assertThat(p).isNotNull();
            assertThat(p.get("title").asString()).isEqualTo("Personalized Greeting");
            assertThat(p.has("arguments")).isTrue();
            assertThat(p.get("arguments").size()).isEqualTo(1);
            assertThat(p.get("arguments").get(0).get("name").asString()).isEqualTo("name");
        }
    }

    private static @Nullable JsonNode findByName(JsonNode items, String name) {
        for (var item : items) {
            if (item.get("name").asString().equals(name)) {
                return item;
            }
        }
        return null;
    }

    private static PromptArgument parseArgument(String spec) {
        var parts = spec.split(":", -1);
        var name = parts[0];
        var description = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
        var required = parts.length > 2 && !parts[2].isEmpty() ? Boolean.parseBoolean(parts[2]) : null;
        return PromptArgument.of(name, null, description, required);
    }
}
