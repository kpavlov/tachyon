/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.extensions.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathSkillsRegistryTest {

    @Test
    void scansClasspathDirectoryOfSkills() {
        var registry = new ClasspathSkillsRegistry("skills");

        assertThat(registry.skills())
                .extracting(SkillsRegistry.Skill::skillPath)
                .containsExactly("git-workflow", "pdf-processing");
        assertThat(registry.readFile("skill://pdf-processing/templates/invoice.md"))
                .isNotEmpty();
    }

    @Test
    void servesSingleClasspathSkillUnderNestedPath() {
        var registry = new ClasspathSkillsRegistry("skills/git-workflow", "team/git-workflow");

        assertThat(registry.skills())
                .extracting(SkillsRegistry.Skill::skillPath)
                .containsExactly("team/git-workflow");
        assertThat(registry.skills().get(0).frontmatter()).containsEntry("name", "git-workflow");
    }

    @Test
    void rejectsUnknownResource() {
        assertThatThrownBy(() -> new ClasspathSkillsRegistry("no-such-resource"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void scansSkillsFromJar(@TempDir Path tempDir) throws Exception {
        var jarPath = tempDir.resolve("skills.jar");
        writeTestJar(jarPath);
        try (var loader = new URLClassLoader(new URL[] {jarPath.toUri().toURL()}, null)) {
            var registry = new ClasspathSkillsRegistry(loader, "bundled", null);

            assertThat(registry.skills())
                    .extracting(SkillsRegistry.Skill::skillPath)
                    .containsExactly("demo");
            assertThat(registry.skills().get(0).files())
                    .extracting(SkillsRegistry.SkillFile::relativePath)
                    .containsExactly("SKILL.md", "guide.md");
            assertThat(new String(registry.readFile("skill://demo/guide.md"), StandardCharsets.UTF_8))
                    .contains("usage");
        }
    }

    @Test
    void servesSingleSkillFromJar(@TempDir Path tempDir) throws Exception {
        var jarPath = tempDir.resolve("skills.jar");
        writeTestJar(jarPath);
        try (var loader = new URLClassLoader(new URL[] {jarPath.toUri().toURL()}, null)) {
            var registry = new ClasspathSkillsRegistry(loader, "bundled/demo", "demo");

            assertThat(registry.skills())
                    .extracting(SkillsRegistry.Skill::skillPath)
                    .containsExactly("demo");
            assertThat(registry.readFile("skill://demo/guide.md")).isNotEmpty();
        }
    }

    private static void writeTestJar(Path jarPath) throws IOException {
        try (var out = new JarOutputStream(Files.newOutputStream(jarPath))) {
            out.putNextEntry(new JarEntry("bundled/"));
            out.closeEntry();
            out.putNextEntry(new JarEntry("bundled/demo/"));
            out.closeEntry();
            out.putNextEntry(new JarEntry("bundled/demo/SKILL.md"));
            out.write("""
                    ---
                    name: demo
                    description: A demo skill
                    ---
                    # Demo
                    """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("bundled/demo/guide.md"));
            out.write("# Guide\nusage\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
