/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.extensions.skills;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import dev.tachyonmcp.core.server.ServerBuilder;
import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.testkit.Mcp20260728Client;
import dev.tachyonmcp.testkit.McpTestServers;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * End-to-end test of the SEP-2640 skills extension over MCP 2026-07-28: skills served from the
 * classpath and from the filesystem, enumerated via {@code skills/list}, fetched via
 * {@code skills/get}, read as {@code skill://} resources, and navigated via
 * {@code resources/directory/read}, with per-request extension negotiation.
 */
class SkillsExtensionE2eTest {

    private static final Path FIXTURES = Path.of(
            URI.create(SkillsExtensionE2eTest.class.getResource("/skills").toString()));

    private TachyonServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void classpathSkillsListedWithDigests() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addClasspathSkillDir("skills").build()));

        try (var client = createClient()) {
            // language=JSON
            var list = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"skills/list","params":{"_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));
            // language=JSON
            assertThatJson(list.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "result":{
                    "skills":[
                      {
                        "uri":"skill://git-workflow/SKILL.md",
                        "frontmatter":{
                          "name":"git-workflow",
                          "description":"Follow this team's Git conventions for branching and commits"
                        },
                        "resources":[
                          {"uri":"skill://git-workflow/SKILL.md","digest":"sha256:b9de7cc1f03a390dd4ee3b2881a13eb5e39f02ec5f44ffb0ab9fb91e10c08d67"},
                          {"uri":"skill://git-workflow/references/BRANCHING.md","digest":"sha256:c23e5f309d54105cc561675ce4384fa62971e00919fe9bd297a37e443746c24e"}
                        ]
                      },
                      {
                        "uri":"skill://pdf-processing/SKILL.md",
                        "frontmatter":{
                          "name":"pdf-processing",
                          "description":"Extract, fill, and assemble PDF documents",
                          "metadata":{"version":"2.1.0"}
                        },
                        "resources":[
                          {"uri":"skill://pdf-processing/SKILL.md","digest":"sha256:da96519e26e173b406339e31ccf3adb0b0bd45c5fdfbabe335bf2ded216b2635"},
                          {"uri":"skill://pdf-processing/scripts/extract.py","digest":"sha256:f05fea0e15cb5f951049570d4cebb3a84b272fd3390c85e8be7586f84f0b68f8"},
                          {"uri":"skill://pdf-processing/templates/invoice.md","digest":"sha256:cd1a5be9eb7a5a46feea259ca26620f73dbd3587cc5111da44fff6489993c643"}
                        ]
                      }
                    ]
                  }
                }
                """);
        }
    }

    @Test
    void classpathSkillGetReturnsRequestedSkill() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addClasspathSkillDir("skills").build()));

        try (var client = createClient()) {
            // language=JSON
            var get = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"skills/get","params":{"uri":"skill://pdf-processing/SKILL.md","_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));
            // language=JSON
            assertThatJson(get.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "result":{
                    "skill":{
                      "uri":"skill://pdf-processing/SKILL.md",
                      "frontmatter":{
                        "name":"pdf-processing",
                        "description":"Extract, fill, and assemble PDF documents",
                        "metadata":{"version":"2.1.0"}
                      },
                      "resources":[
                        {"uri":"skill://pdf-processing/SKILL.md","digest":"sha256:da96519e26e173b406339e31ccf3adb0b0bd45c5fdfbabe335bf2ded216b2635"},
                        {"uri":"skill://pdf-processing/scripts/extract.py","digest":"sha256:f05fea0e15cb5f951049570d4cebb3a84b272fd3390c85e8be7586f84f0b68f8"},
                        {"uri":"skill://pdf-processing/templates/invoice.md","digest":"sha256:cd1a5be9eb7a5a46feea259ca26620f73dbd3587cc5111da44fff6489993c643"}
                      ]
                    }
                  }
                }
                """);
        }
    }

    @Test
    void classpathSkillFileReadAsTextResource() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addClasspathSkillDir("skills").build()));

        try (var client = createClient()) {
            // language=JSON
            var read = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"resources/read","params":{"uri":"skill://git-workflow/SKILL.md","_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));
            // language=JSON
            assertThatJson(read.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "result":{
                    "contents":[
                      {"uri":"skill://git-workflow/SKILL.md","mimeType":"text/markdown","text":"---\\nname: git-workflow\\ndescription: Follow this team's Git conventions for branching and commits\\n---\\n\\n# Git Workflow\\n\\nFollow this team's Git conventions for branching and commits.\\nUse the branching guide in `references/BRANCHING.md`.\\n"}
                    ],
                    "resultType":"complete",
                    "ttlMs":0,
                    "cacheScope":"public"
                  }
                }
                """);
        }
    }

    @Test
    void classpathSkillDirectoryListsRootChildren() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addClasspathSkillDir("skills").build()));

        try (var client = createClient()) {
            // language=JSON
            var directory = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"resources/directory/read","params":{"uri":"skill://pdf-processing","_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));
            // language=JSON
            assertThatJson(directory.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "result":{
                    "resources":[
                      {"uri":"skill://pdf-processing/SKILL.md","name":"SKILL.md","mimeType":"text/markdown"},
                      {"uri":"skill://pdf-processing/scripts","name":"scripts","mimeType":"inode/directory"},
                      {"uri":"skill://pdf-processing/templates","name":"templates","mimeType":"inode/directory"}
                    ]
                  }
                }
                """);
        }
    }

    @Test
    void classpathSkillDirectoryListsNestedChildren() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addClasspathSkillDir("skills").build()));

        try (var client = createClient()) {
            // language=JSON
            var nestedDirectory = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"resources/directory/read","params":{"uri":"skill://pdf-processing/scripts","_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));
            // language=JSON
            assertThatJson(nestedDirectory.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "result":{
                    "resources":[
                      {"uri":"skill://pdf-processing/scripts/extract.py","name":"extract.py","mimeType":"text/plain"}
                    ]
                  }
                }
                """);
        }
    }

    @Test
    void fileSystemSkillsServed() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addSkillDir(FIXTURES).build()));

        try (var client = createClient()) {
            // language=JSON
            var response = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"skills/list","params":{"_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));

            // language=JSON
            assertThatJson(response.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "result":{
                    "skills":[
                      {
                        "uri":"skill://git-workflow/SKILL.md",
                        "frontmatter":{
                          "name":"git-workflow",
                          "description":"Follow this team's Git conventions for branching and commits"
                        },
                        "resources":[
                          {"uri":"skill://git-workflow/SKILL.md","digest":"sha256:b9de7cc1f03a390dd4ee3b2881a13eb5e39f02ec5f44ffb0ab9fb91e10c08d67"},
                          {"uri":"skill://git-workflow/references/BRANCHING.md","digest":"sha256:c23e5f309d54105cc561675ce4384fa62971e00919fe9bd297a37e443746c24e"}
                        ]
                      },
                      {
                        "uri":"skill://pdf-processing/SKILL.md",
                        "frontmatter":{
                          "name":"pdf-processing",
                          "description":"Extract, fill, and assemble PDF documents",
                          "metadata":{"version":"2.1.0"}
                        },
                        "resources":[
                          {"uri":"skill://pdf-processing/SKILL.md","digest":"sha256:da96519e26e173b406339e31ccf3adb0b0bd45c5fdfbabe335bf2ded216b2635"},
                          {"uri":"skill://pdf-processing/scripts/extract.py","digest":"sha256:f05fea0e15cb5f951049570d4cebb3a84b272fd3390c85e8be7586f84f0b68f8"},
                          {"uri":"skill://pdf-processing/templates/invoice.md","digest":"sha256:cd1a5be9eb7a5a46feea259ca26620f73dbd3587cc5111da44fff6489993c643"}
                        ]
                      }
                    ]
                  }
                }
                """);
        }
    }

    @Test
    void singleSkillsUnderExplicitPaths() throws Exception {
        startServer(builder -> builder.extension(SkillsExtension.builder()
                .addSkill(FIXTURES.resolve("git-workflow"), "team/git-workflow")
                .addClasspathSkill("skills/pdf-processing", "acme/pdf-processing")
                .build()));

        try (var client = createClient()) {
            // language=JSON
            var response = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"skills/list","params":{"_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));

            // language=JSON
            assertThatJson(response.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "result":{
                    "skills":[
                      {
                        "uri":"skill://team/git-workflow/SKILL.md",
                        "frontmatter":{
                          "name":"git-workflow",
                          "description":"Follow this team's Git conventions for branching and commits"
                        },
                        "resources":[
                          {"uri":"skill://team/git-workflow/SKILL.md","digest":"sha256:b9de7cc1f03a390dd4ee3b2881a13eb5e39f02ec5f44ffb0ab9fb91e10c08d67"},
                          {"uri":"skill://team/git-workflow/references/BRANCHING.md","digest":"sha256:c23e5f309d54105cc561675ce4384fa62971e00919fe9bd297a37e443746c24e"}
                        ]
                      },
                      {
                        "uri":"skill://acme/pdf-processing/SKILL.md",
                        "frontmatter":{
                          "name":"pdf-processing",
                          "description":"Extract, fill, and assemble PDF documents",
                          "metadata":{"version":"2.1.0"}
                        },
                        "resources":[
                          {"uri":"skill://acme/pdf-processing/SKILL.md","digest":"sha256:da96519e26e173b406339e31ccf3adb0b0bd45c5fdfbabe335bf2ded216b2635"},
                          {"uri":"skill://acme/pdf-processing/scripts/extract.py","digest":"sha256:f05fea0e15cb5f951049570d4cebb3a84b272fd3390c85e8be7586f84f0b68f8"},
                          {"uri":"skill://acme/pdf-processing/templates/invoice.md","digest":"sha256:cd1a5be9eb7a5a46feea259ca26620f73dbd3587cc5111da44fff6489993c643"}
                        ]
                      }
                    ]
                  }
                }
                """);
        }
    }

    @Test
    void methodsHiddenUntilExtensionDeclared() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addClasspathSkillDir("skills").build()));

        try (var client = new Mcp20260728Client(server.port())) {
            // language=JSON
            var list = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"skills/list","params":{}}
                """);
            // language=JSON
            assertThatJson(list.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
                """);

            // language=JSON
            var read = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"skill://git-workflow/SKILL.md"}}
                """);
            // language=JSON
            assertThatJson(read.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"Resource not found"}}
                """);

            // language=JSON
            var resources = client.post("""
                {"jsonrpc":"2.0","id":3,"method":"resources/list","params":{}}
                """);
            // language=JSON
            assertThatJson(resources.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":3,
                  "result":{
                    "resources":[],
                    "resultType":"complete",
                    "ttlMs":0,
                    "cacheScope":"public"
                  }
                }
                """);
        }
    }

    @Test
    void unknownSkillsAndDirectoriesFail() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addClasspathSkillDir("skills").build()));

        try (var client = createClient()) {
            // language=JSON
            var get = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"skills/get","params":{"uri":"skill://nope/SKILL.md","_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));
            // language=JSON
            assertThatJson(get.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Unknown skill: skill://nope/SKILL.md"}}
                """);

            // language=JSON
            var directory = client.post("""
                {"jsonrpc":"2.0","id":2,"method":"resources/directory/read","params":{"uri":"skill://git-workflow/SKILL.md","_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));
            // language=JSON
            assertThatJson(directory.body()).isEqualTo("""
                {"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"Unknown skill directory: skill://git-workflow/SKILL.md"}}
                """);
        }
    }

    @Test
    void skillResourcesVisibleInResourcesListWhenDeclared() throws Exception {
        startServer(builder -> builder.extension(
                SkillsExtension.builder().addClasspathSkillDir("skills").build()));

        try (var client = createClient()) {
            // language=JSON
            var response = client.post("""
                {"jsonrpc":"2.0","id":1,"method":"resources/list","params":{"_meta":{"%s":{}}}}
                """.formatted(SkillsExtension.ID));

            // language=JSON
            assertThatJson(response.body()).isEqualTo("""
                {
                  "jsonrpc":"2.0",
                  "id":1,
                  "result":{
                    "resources":[
                      {"uri":"skill://git-workflow/SKILL.md","name":"git-workflow/SKILL.md","description":"Follow this team's Git conventions for branching and commits","mimeType":"text/markdown"},
                      {"uri":"skill://git-workflow/references/BRANCHING.md","name":"git-workflow/references/BRANCHING.md","mimeType":"text/markdown"},
                      {"uri":"skill://pdf-processing/SKILL.md","name":"pdf-processing/SKILL.md","description":"Extract, fill, and assemble PDF documents","mimeType":"text/markdown"},
                      {"uri":"skill://pdf-processing/scripts/extract.py","name":"pdf-processing/scripts/extract.py","mimeType":"text/plain"},
                      {"uri":"skill://pdf-processing/templates/invoice.md","name":"pdf-processing/templates/invoice.md","mimeType":"text/markdown"}
                    ],
                    "resultType":"complete",
                    "ttlMs":0,
                    "cacheScope":"public"
                  }
                }
                """);
        }
    }

    private void startServer(Consumer<ServerBuilder> configurer) {
        var started = McpTestServers.start(configurer, server -> {});
        this.server = started;
    }

    private Mcp20260728Client createClient() {
        return new Mcp20260728Client(server.port())
                .withExtensions(Map.of(SkillsExtension.ID, JsonNodeFactory.instance.objectNode()));
    }
}
