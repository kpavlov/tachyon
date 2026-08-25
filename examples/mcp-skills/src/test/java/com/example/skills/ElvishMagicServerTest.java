/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package com.example.skills;

import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.extensions.skills.SkillsExtension;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ElvishMagicServerTest {

    private final TachyonServer server = ElvishMagicServer.buildServer("localhost", 0);
    private Mcp20260728Client client;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    void startServer() {
        server.start();
        client = new Mcp20260728Client(server.port())
            .withExtensions(Map.of(SkillsExtension.ID, JsonNodeFactory.instance.objectNode()));
    }

    @AfterAll
    void stopServer() {
        client.close();
        server.close();
    }

    @Test
    void shouldAdvertiseSkills() throws Exception {
        var response = client.post("""
            {
              "jsonrpc":"2.0",
              "id":1,
              "method":"skills/list",
              "params":{"_meta":{"io.modelcontextprotocol/skills":{}}}
            }
            """);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThatJson(response.body())
            .whenIgnoringPaths("result.skills[*].resources[*].size")
            .isEqualTo("""
            {
              "jsonrpc":"2.0",
              "id":1,
              "result":{
                "skills":[
                  {
                    "uri":"skill://elvish-healing/SKILL.md",
                    "frontmatter":{
                      "name":"elvish-healing",
                      "description":"Create gentle, fictional Elvish remedies for fantasy stories and games",
                      "metadata":{"tradition":"silver-leaf"}
                    },
                    "resources":[
                      {
                        "uri":"skill://elvish-healing/SKILL.md",
                        "digest":"sha256:149f0a3d6dab82e228c17c4e17695664da201cd722bf7ef878b47d3a898e7784",
                        "size":543
                      },
                      {
                        "uri":"skill://elvish-healing/references/HEALERS-BOOK.md",
                        "digest":"sha256:4bcbf909aebb4546f2e193ceb83206553cd28b9fd4adeb50627a995a03863dd6",
                        "size":350
                      }
                    ]
                  },
                  {
                    "uri":"skill://elvish-magic/SKILL.md",
                    "frontmatter":{
                      "name":"elvish-magic",
                      "description":"Compose gentle, fictional Elvish spells for light, water, and growing things",
                      "metadata":{"tradition":"starlit-grove"}
                    },
                    "resources":[{
                      "uri":"skill://elvish-magic/SKILL.md",
                      "digest":"sha256:3679a7ca0e2ff7a55ae17367c73b6f20eea7a12c62a8f8ad7e1811bbdc1fda9a",
                      "size":603
                    }]
                  }
                ],
                "resultType":"complete",
                "ttlMs":0,
                "cacheScope":"public"
              }
            }
            """);
    }

    @Test
    void shouldReadSkillAsResource() throws Exception {
        var skill = """
            ---
            name: elvish-magic
            description: Compose gentle, fictional Elvish spells for light, water, and growing things
            metadata:
              tradition: starlit-grove
            ---

            # Elvish Magic

            Create lyrical, harmless magic for fantasy stories and games.

            ## Casting

            1. Ask for the intent, target, and duration when any are missing.
            2. Choose one school: starlight, water-song, or greenweaving.
            3. Give the spell an original Elvish-sounding name and a one-line invocation.
            4. Describe the visible effect and how it gently ends.

            Never control minds, harm living beings, or imitate magic from an existing fictional universe.
            """;
        var response = client.sendRpc("""
            {
              "jsonrpc":"2.0",
              "id":2,
              "method":"resources/read",
              "params":{
                "uri":"skill://elvish-magic/SKILL.md",
                "_meta":{"io.modelcontextprotocol/skills":{}}
              }
            }
            """);

        assertThatJson(response).isEqualTo("""
            {
              "jsonrpc":"2.0",
              "id":2,
              "result":{
                "contents":[{
                  "uri":"skill://elvish-magic/SKILL.md",
                  "mimeType":"text/markdown",
                  "text":%s
                }],
                "resultType":"complete",
                "ttlMs":0,
                "cacheScope":"public"
              }
            }
            """.formatted(JSON.writeValueAsString(skill)));
    }

    @Test
    void shouldReadHealersBookAsResource() throws Exception {
        var book = """
            # The Silver-Leaf Healer's Book

            ## Moonpetal Tea

            For weariness after a long journey. Its steam glows softly and smells of rain.

            ## Starwater Compress

            For small fictional bruises. The cloth cools as silver ripples cross its surface.

            ## Dawnmoss Poultice

            For a fantasy hero recovering from exertion. The moss brightens when rest is still needed.
            """;
        var response = client.sendRpc("""
            {
              "jsonrpc":"2.0",
              "id":3,
              "method":"resources/read",
              "params":{"uri":"skill://elvish-healing/references/HEALERS-BOOK.md"}
            }
            """);

        assertThatJson(response).isEqualTo("""
            {
              "jsonrpc":"2.0",
              "id":3,
              "result":{
                "contents":[{
                  "uri":"skill://elvish-healing/references/HEALERS-BOOK.md",
                  "mimeType":"text/markdown",
                  "text":%s
                }],
                "resultType":"complete",
                "ttlMs":0,
                "cacheScope":"public"
              }
            }
            """.formatted(JSON.writeValueAsString(book)));
    }
}
