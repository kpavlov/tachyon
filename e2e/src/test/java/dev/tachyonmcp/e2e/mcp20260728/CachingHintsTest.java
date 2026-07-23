/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e.mcp20260728;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.e2e.AbstractStatelessMcpE2eTest;
import dev.tachyonmcp.server.domain.TextResourceContents;
import dev.tachyonmcp.server.features.prompts.PromptDescriptor;
import dev.tachyonmcp.server.features.resources.ResourceDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 requires {@code ttlMs}/{@code cacheScope} caching hints (SEP-2549) on every
 * {@code resultType: "complete"} result from {@code tools/list}, {@code prompts/list},
 * {@code resources/list}, {@code resources/templates/list}, and {@code resources/read}.
 * Regression test for {@code v2026_07_28.codecs.McpResponseMapper} only overriding
 * {@code discoverResult} and falling through to the 2025-11-25 record shapes (no caching fields)
 * for every other list/read method.
 */
class CachingHintsTest extends AbstractStatelessMcpE2eTest {

    @BeforeEach
    void registerFixtures() {
        startServer(b -> b.capabilities(c -> c.tools().resources().prompts())
                .resource(
                        ResourceDescriptor.of("hello", "hello://world", "Hello resource", "text/plain"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "Hello, World!", "text/plain"))
                .resourceTemplate(
                        builder -> builder.name("tmpl")
                                .uriTemplate("test://tmpl/{id}")
                                .description("d"),
                        (ctx, request) -> TextResourceContents.of(request.uri(), "x", "text/plain"))
                .prompt(PromptDescriptor.of("greeting", "A greeting"), java.util.List.of()));
    }

    private void assertCachingHints(String jsonBody) {
        assertThatJson(jsonBody).inPath("$.result.ttlMs").isEqualTo(0);
        assertThatJson(jsonBody).inPath("$.result.cacheScope").isEqualTo("public");
    }

    @Test
    void toolsListIncludesCachingHints() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}
                    """);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertCachingHints(response.body());
        }
    }

    @Test
    void promptsListIncludesCachingHints() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 2, "method": "prompts/list"}
                    """);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertCachingHints(response.body());
        }
    }

    @Test
    void resourcesListIncludesCachingHints() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 3, "method": "resources/list"}
                    """);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertCachingHints(response.body());
        }
    }

    @Test
    void resourceTemplatesListIncludesCachingHints() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 4, "method": "resources/templates/list"}
                    """);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertCachingHints(response.body());
        }
    }

    @Test
    void resourceReadIncludesCachingHints() throws Exception {
        try (var client = createModernTestClient()) {
            var response = client.post("""
                    {"jsonrpc": "2.0", "id": 5, "method": "resources/read", "params": {"uri": "hello://world"}}
                    """);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertCachingHints(response.body());
        }
    }
}
