/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.extensions.skills;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tachyonmcp.api.server.domain.RequestId;
import dev.tachyonmcp.core.protocol.Protocols;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.ClientCapabilities;
import dev.tachyonmcp.core.protocol.mcp.v2025_11_25.models.InitializeRequestParams;
import dev.tachyonmcp.core.server.McpDispatcher;
import dev.tachyonmcp.core.server.TachyonServer;
import dev.tachyonmcp.core.server.internal.ServerEngine;
import dev.tachyonmcp.core.server.session.DefaultDispatchContext;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class SkillsExtensionTest {

    private static final Path FIXTURES =
            Path.of(URI.create(SkillsExtensionTest.class.getResource("/skills").toString()));

    private ServerEngine server;
    private TachyonServer tachyon;
    private McpDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        tachyon = TachyonServer.builder()
                .extension(SkillsExtension.builder().addSkillDir(FIXTURES).build())
                .build();
        server = (ServerEngine) tachyon;
        dispatcher = new McpDispatcher(server, server.executor());
    }

    @Test
    void advertisesExtensionAndDirectoryRead() {
        var extension = (SkillsExtension) server.extensions().get(0);
        assertThat(extension.extensionId()).isEqualTo(SkillsExtension.ID);
        assertThat(extension.serverSettings().values().boolValue("directoryRead"))
                .isTrue();
    }

    @Test
    void registersEverySkillFileAsExtensionOwnedResource() {
        var descriptors = server.resources().descriptors();
        assertThat(descriptors)
                .extracting(descriptor -> descriptor.uri())
                .contains(
                        "skill://git-workflow/SKILL.md",
                        "skill://git-workflow/references/BRANCHING.md",
                        "skill://pdf-processing/scripts/extract.py",
                        "skill://pdf-processing/templates/invoice.md");
        assertThat(descriptors)
                .allSatisfy(descriptor -> assertThat(descriptor.extensionId()).isEqualTo(SkillsExtension.ID));
        assertThat(server.resources().findByUri("skill://git-workflow/SKILL.md"))
                .get()
                .extracting(descriptor -> descriptor.description())
                .isEqualTo("Follow this team's Git conventions for branching and commits");
    }

    @Test
    void ownsTheSkillMethods() {
        assertThat(server.extensionForMethod("skills/list")).isEqualTo(SkillsExtension.ID);
        assertThat(server.extensionForMethod("skills/get")).isEqualTo(SkillsExtension.ID);
        assertThat(server.extensionForMethod("resources/directory/read")).isEqualTo(SkillsExtension.ID);
    }

    @Test
    void rejectsSkillMethodsWhenNotNegotiated() throws Exception {
        var body = dispatch("skills/list", Map.of(), false);

        assertThat(body).contains("\"error\"");
        assertThat(body).contains("-32601");
    }

    @Test
    void listsSkillsWhenNegotiated() throws Exception {
        var body = dispatch("skills/list", Map.of("_meta", Map.of(SkillsExtension.ID, Map.of())));

        assertThat(body)
                .contains("\"skill://git-workflow/SKILL.md\"")
                .contains("\"skill://pdf-processing/SKILL.md\"")
                .contains("\"sha256:c23e5f309d54105cc561675ce4384fa62971e00919fe9bd297a37e443746c24e\"");
    }

    @Test
    void getsSkillByUri() throws Exception {
        var body = dispatch(
                "skills/get",
                Map.of("uri", "skill://pdf-processing/SKILL.md", "_meta", Map.of(SkillsExtension.ID, Map.of())));

        assertThat(body).contains("\"frontmatter\"").contains("\"name\":\"pdf-processing\"");
    }

    @Test
    void rejectsUnknownSkillUri() throws Exception {
        var body = dispatch(
                "skills/get", Map.of("uri", "skill://nope/SKILL.md", "_meta", Map.of(SkillsExtension.ID, Map.of())));

        assertThat(body).contains("-32602");
    }

    @Test
    void listsDirectoryChildren() throws Exception {
        var body = dispatch(
                "resources/directory/read",
                Map.of("uri", "skill://pdf-processing", "_meta", Map.of(SkillsExtension.ID, Map.of())));

        assertThat(body)
                .contains("\"skill://pdf-processing/scripts\"")
                .contains("\"inode/directory\"")
                .contains("\"skill://pdf-processing/SKILL.md\"");
    }

    @Test
    void readsSkillFileContent() throws Exception {
        var body = dispatch(
                "resources/read",
                Map.of("uri", "skill://git-workflow/SKILL.md", "_meta", Map.of(SkillsExtension.ID, Map.of())));

        assertThat(body).contains("Follow this team's Git conventions");
    }

    private String dispatch(String method, Map<String, Object> rawParams) throws Exception {
        return dispatch(method, rawParams, true);
    }

    private String dispatch(String method, Map<String, Object> rawParams, boolean negotiate) throws Exception {
        var context = DefaultDispatchContext.create(Protocols.list().get(0), server);
        var session = server.createSession("sess_skills");
        context.setSession(session);
        if (negotiate) {
            var handler = server.getHandler("initialize");
            var caps = ClientCapabilities.builder()
                    .extensions(Map.of(SkillsExtension.ID, JsonNodeFactory.instance.objectNode()))
                    .build();
            var params = InitializeRequestParams.builder()
                    .protocolVersion("2025-11-25")
                    .capabilities(caps)
                    .build();
            handler.handle(context, params);
        }
        session.activate();
        var result = (McpDispatcher.DispatchResult.Response) dispatcher
                .dispatchRequestAsync(RequestId.of(1), method, rawParams, session.id(), null, context)
                .join();
        return result.responseBodyString();
    }
}
