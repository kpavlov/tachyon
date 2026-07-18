/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ListPaginationE2eTest extends AbstractStatelessMcpE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ResourceHandler EMPTY_RESOURCE =
            (ctx, rawUri, params, uriTemplate) -> TextResourceContents.of(rawUri, "text/plain", "");

    @Test
    void resourcesListReturnsConfiguredPageSize() throws Exception {
        startServer(it -> it.capabilities(c -> c.resources().resourcesPageSize(2))
                .resource(resource("res-a"), EMPTY_RESOURCE)
                .resource(resource("res-b"), EMPTY_RESOURCE)
                .resource(resource("res-c"), EMPTY_RESOURCE));

        try (var client = createTestClient()) {
            client.initialize();

            var page1 = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"resources/list"}
                """);
            var root1 = MAPPER.readTree(page1.body());
            assertThat(root1.at("/result/resources").size()).isEqualTo(2);
            var cursor = root1.at("/result/nextCursor").asString(null);
            assertThat(cursor).isNotNull();

            var page2 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/list","params":{"cursor":"%s"}}
                """.formatted(cursor));
            var root2 = MAPPER.readTree(page2.body());
            assertThat(root2.at("/result/resources").size()).isEqualTo(1);
            assertThat(root2.at("/result/nextCursor").asString(null)).isNull();
        }
    }

    @Test
    void resourcesListRejectsInvalidCursor() throws Exception {
        startServer(it ->
                it.capabilities(c -> c.resources().resourcesPageSize(2)).resource(resource("res-a"), EMPTY_RESOURCE));

        try (var client = createTestClient()) {
            client.initialize();

            var response = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"resources/list","params":{"cursor":"garbage-cursor-xyz"}}
                """);
            var root = MAPPER.readTree(response.body());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(root.at("/error/code").asInt()).isEqualTo(-32602);
            assertThat(root.at("/error/message").asString()).isEqualTo("Invalid cursor");
        }
    }

    @Test
    void promptsListReturnsConfiguredPageSize() throws Exception {
        startServer(it -> it.capabilities(c -> c.prompts().promptsPageSize(2))
                .prompt(PromptDescriptor.of("p-a", null), List.of())
                .prompt(PromptDescriptor.of("p-b", null), List.of())
                .prompt(PromptDescriptor.of("p-c", null), List.of()));

        try (var client = createTestClient()) {
            client.initialize();

            var page1 = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"prompts/list"}
                """);
            var root1 = MAPPER.readTree(page1.body());
            assertThat(root1.at("/result/prompts").size()).isEqualTo(2);
            var cursor = root1.at("/result/nextCursor").asString(null);
            assertThat(cursor).isNotNull();

            var page2 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"prompts/list","params":{"cursor":"%s"}}
                """.formatted(cursor));
            var root2 = MAPPER.readTree(page2.body());
            assertThat(root2.at("/result/prompts").size()).isEqualTo(1);
            assertThat(root2.at("/result/nextCursor").asString(null)).isNull();
        }
    }

    @Test
    void tasksListReturnsConfiguredPageSize() throws Exception {
        startServer(it -> it.capabilities(c -> c.tasks(true, false, false).tasksPageSize(2)));
        server.tasks().create();
        server.tasks().create();
        server.tasks().create();

        try (var client = createTestClient()) {
            client.initialize();

            var page1 = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"tasks/list"}
                """);
            var root1 = MAPPER.readTree(page1.body());
            assertThat(root1.at("/result/tasks").size()).isEqualTo(2);
            var cursor = root1.at("/result/nextCursor").asString(null);
            assertThat(cursor).isNotNull();

            var page2 = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"tasks/list","params":{"cursor":"%s"}}
                """.formatted(cursor));
            var root2 = MAPPER.readTree(page2.body());
            assertThat(root2.at("/result/tasks").size()).isEqualTo(1);
            assertThat(root2.at("/result/nextCursor").asString(null)).isNull();
        }
    }

    private static ResourceDescriptor resource(String name) {
        return ResourceDescriptor.of(name, "test://" + name, null, null);
    }
}
