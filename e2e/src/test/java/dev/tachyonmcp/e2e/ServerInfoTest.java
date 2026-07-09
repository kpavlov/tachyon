/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerInfoTest extends AbstractMcpE2eTest {

    @Test
    void allCapabilitiesEnabled() throws Exception {
        startServer(it -> it.info(b -> b.name("test-server")
                        .version("2.0")
                        .description("Test server")
                        .title("Test Server")
                        .websiteUrl("https://example.com/mcp")
                        .instructions("Test instructions")
                        .icons(Icon.of("https://example.com/icon.png", "image/png", List.of("32x32"), "light")))
                .capabilities(c -> c.completions()
                        .logging()
                        .prompts(true)
                        .tools(true)
                        .resources(true, true)
                        .prompts(true)
                        .tasks(true, true, true)));

        try (var client = createTestClient()) {
            // language=json
            final var response = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}
                """);

            // language=json
            final var expected = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "protocolVersion": "2025-11-25",
                    "serverInfo": {
                      "name": "test-server",
                      "version": "2.0",
                      "description": "Test server",
                      "title": "Test Server",
                      "websiteUrl": "https://example.com/mcp",
                      "icons": [{
                        "src": "https://example.com/icon.png",
                        "mimeType": "image/png",
                        "sizes": ["32x32"],
                        "theme": "light"
                      }]
                    },
                    "instructions": "Test instructions",
                    "capabilities": {
                      "logging": {},
                      "completions": {},
                      "tools": {
                        "listChanged": true
                      },
                      "resources": {
                        "subscribe": true,
                        "listChanged": true
                      },
                      "prompts": {
                        "listChanged": true
                      },
                      "tasks": {
                        "list": {},
                        "cancel": {},
                        "requests": {
                          "tools": {
                            "call": {}
                          }
                        }
                      }
                    }
                  }
                }
                """;

            assertThatJson(response.body()).isEqualTo(expected);
        }
    }

    @Test
    void minimalisticServer() throws Exception {
        startServer(it -> it.capabilities(c -> c.noTools().noResources().noPrompts()));

        try (var client = createTestClient()) {
            // language=json
            final var response = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}
                """);

            // language=json
            final var expected = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "protocolVersion":"2025-11-25",
                    "serverInfo":{"version":"0.1","name":"tachyon-mcp"},
                    "capabilities": {}
                  }
                }
                """;

            assertThatJson(response.body()).isEqualTo(expected);
        }
    }
}
