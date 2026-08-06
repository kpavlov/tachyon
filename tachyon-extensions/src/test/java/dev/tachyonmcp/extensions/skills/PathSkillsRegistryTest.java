/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.extensions.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathSkillsRegistryTest {

    private static final Path FIXTURES = Path.of(
            URI.create(PathSkillsRegistryTest.class.getResource("/skills").toString()));

    @Test
    void scansDirectoryOfSkills() throws Exception {
        var registry = new PathSkillsRegistry(FIXTURES);

        assertThat(registry.skills())
                .extracting(SkillsRegistry.Skill::skillPath)
                .containsExactly("git-workflow", "pdf-processing");

        var git = registry.skills().get(0);
        assertThat(git.skillUri()).isEqualTo("skill://git-workflow/SKILL.md");
        assertThat(git.frontmatter()).containsEntry("name", "git-workflow");
        assertThat(git.files())
                .extracting(SkillsRegistry.SkillFile::relativePath)
                .containsExactly("SKILL.md", "references/BRANCHING.md");
        assertThat(git.files().get(0).uri()).isEqualTo("skill://git-workflow/SKILL.md");
        assertThat(git.files().get(0).mimeType()).isEqualTo("text/markdown");
        assertThat(git.files().get(0).digest())
                .isEqualTo("sha256:b9de7cc1f03a390dd4ee3b2881a13eb5e39f02ec5f44ffb0ab9fb91e10c08d67");
    }

    @Test
    void readsFileBytesByUri() throws Exception {
        var registry = new PathSkillsRegistry(FIXTURES);

        var content = new String(registry.readFile("skill://git-workflow/SKILL.md"), StandardCharsets.UTF_8);
        assertThat(content).contains("Follow this team's Git conventions");

        assertThat(registry.readFile("skill://git-workflow/missing.md")).isNull();
    }

    @Test
    void skipsSubdirectoriesWithoutSkillMd(@TempDir Path tempDir) throws Exception {
        Files.createDirectory(tempDir.resolve("not-a-skill"));
        Files.writeString(tempDir.resolve("not-a-skill/README.md"), "no skill here");

        var registry = new PathSkillsRegistry(tempDir);

        assertThat(registry.skills()).isEmpty();
    }

    @Test
    void rejectsDirectoryWithoutSkillMdWhenStrict(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "no skill here");

        assertThatThrownBy(() -> new PathSkillsRegistry(tempDir, "git-workflow"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKILL.md");
    }

    @Test
    void rejectsNameMismatchBetweenPathAndFrontmatter() throws Exception {
        assertThatThrownBy(() -> new PathSkillsRegistry(FIXTURES.resolve("git-workflow"), "renamed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must equal frontmatter 'name'");
    }

    @Test
    void servesSingleSkillUnderNestedPath() throws Exception {
        var registry = new PathSkillsRegistry(FIXTURES.resolve("git-workflow"), "team/git-workflow");

        assertThat(registry.skills())
                .extracting(SkillsRegistry.Skill::skillPath)
                .containsExactly("team/git-workflow");
        assertThat(registry.skills().get(0).skillUri()).isEqualTo("skill://team/git-workflow/SKILL.md");
        assertThat(registry.readFile("skill://team/git-workflow/references/BRANCHING.md"))
                .isNotEmpty();
    }

    @Test
    void returnsBinaryBytesForNonTextFiles() throws IOException {
        var registry = new PathSkillsRegistry(FIXTURES);

        var script = registry.skills().stream()
                .filter(skill -> skill.skillPath().equals("pdf-processing"))
                .findFirst()
                .orElseThrow()
                .files()
                .stream()
                .filter(file -> file.relativePath().equals("scripts/extract.py"))
                .findFirst()
                .orElseThrow();
        assertThat(script.mimeType()).isEqualTo("text/plain");
        assertThat(script.digest())
                .isEqualTo("sha256:f05fea0e15cb5f951049570d4cebb3a84b272fd3390c85e8be7586f84f0b68f8");
        assertThat(new String(registry.readFile(script.uri()), StandardCharsets.UTF_8))
                .contains("extract");
    }
}
