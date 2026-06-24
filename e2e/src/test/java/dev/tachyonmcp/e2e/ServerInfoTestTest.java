/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.e2e;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.server.TachyonMcpServer;
import org.junit.jupiter.api.Test;

class ServerInfoTestTest extends AbstractMcpE2eTest {

    @Test
    void allCapabilitiesEnabled() throws Exception {
        startServer(TachyonMcpServer.builder()
                .capabilities(it -> it.completions()
                        .logging()
                        .prompts(true)
                        .tools(true)
                        .resources(true, true)
                        .prompts(true)
                        .tasks(true, true, true))
                .build());

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
                    "serverInfo":{"name":"tachyon-mcp","version":"0.1"},
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
        startServer(TachyonMcpServer.builder()
                .capabilities(it -> it.noTools().noResources().noPrompts())
                .build());

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
