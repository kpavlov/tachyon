/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Verifies that the release version referenced in the public-facing documentation and the
 * bundled example {@code pom.xml} files is consistent.
 *
 * <p>These artifacts (README files, docs, and example poms) hardcode the latest published
 * {@code tachyon-server} release version rather than inheriting {@code ${project.version}}, so a
 * version bump can silently miss one of them. This test pins down the current expected version
 * and guards against both a stale/missed reference and a mismatch between the files.
 */
class ReleaseVersionDocsConsistencyTest {

    private static final String CURRENT_VERSION = "1.0.0-beta.9";
    private static final String PREVIOUS_VERSION = "1.0.0-beta.8";

    private static final Pattern TACHYON_SERVER_DEPENDENCY_VERSION =
            Pattern.compile("<artifactId>tachyon-server</artifactId>\\s*<version>([^<]+)</version>");

    private static final Pattern TACHYON_SERVER_KOTLIN_DEPENDENCY_VERSION =
            Pattern.compile("<artifactId>tachyon-server-kotlin</artifactId>\\s*<version>([^<]+)</version>");

    private static final Pattern TACHYON_SERVER_VERSION_PROPERTY =
            Pattern.compile("<tachyon-server\\.version>([^<]+)</tachyon-server\\.version>");

    private static final Pattern MIGRATION_DOC_VERSION_MENTION =
            Pattern.compile("arrived in\\s*\\*\\*(\\d+\\.\\d+\\.\\d+-beta\\.\\d+)\\*\\*\\s*\u2014\\s*if you wrote a"
                    + " project-side shim");

    @Test
    void rootReadmeDependencySnippetUsesCurrentVersion() {
        assertThat(extractVersion("README.md", TACHYON_SERVER_DEPENDENCY_VERSION)).isEqualTo(CURRENT_VERSION);
    }

    @Test
    void quickstartDocDependencySnippetUsesCurrentVersion() {
        assertThat(extractVersion("docs/quickstart.md", TACHYON_SERVER_DEPENDENCY_VERSION))
                .isEqualTo(CURRENT_VERSION);
    }

    @Test
    void kotlinReadmeDependencySnippetUsesCurrentVersion() {
        assertThat(extractVersion("tachyon-server-kotlin/README.md", TACHYON_SERVER_KOTLIN_DEPENDENCY_VERSION))
                .isEqualTo(CURRENT_VERSION);
    }

    @Test
    void migrationDocMentionsCurrentVersionForOutputSchemaOverload() {
        assertThat(extractVersion("docs/migrate-from-kotlin-mcp-to-tachyon.md", MIGRATION_DOC_VERSION_MENTION))
                .isEqualTo(CURRENT_VERSION);
    }

    @Test
    void echoKotlinExamplePomPinsCurrentVersion() {
        assertThat(extractVersion("examples/echo-kotlin/pom.xml", TACHYON_SERVER_VERSION_PROPERTY))
                .isEqualTo(CURRENT_VERSION);
    }

    @Test
    void weatherExamplePomPinsCurrentVersion() {
        assertThat(extractVersion("examples/weather/pom.xml", TACHYON_SERVER_VERSION_PROPERTY))
                .isEqualTo(CURRENT_VERSION);
    }

    @Test
    void allDocumentationAndExamplesAgreeOnTheSameReleaseVersion() {
        final var extractedVersions =
                Map.of(
                        "README.md", extractVersion("README.md", TACHYON_SERVER_DEPENDENCY_VERSION),
                        "docs/quickstart.md",
                                extractVersion("docs/quickstart.md", TACHYON_SERVER_DEPENDENCY_VERSION),
                        "tachyon-server-kotlin/README.md",
                                extractVersion(
                                        "tachyon-server-kotlin/README.md", TACHYON_SERVER_KOTLIN_DEPENDENCY_VERSION),
                        "docs/migrate-from-kotlin-mcp-to-tachyon.md",
                                extractVersion(
                                        "docs/migrate-from-kotlin-mcp-to-tachyon.md", MIGRATION_DOC_VERSION_MENTION),
                        "examples/echo-kotlin/pom.xml",
                                extractVersion("examples/echo-kotlin/pom.xml", TACHYON_SERVER_VERSION_PROPERTY),
                        "examples/weather/pom.xml",
                                extractVersion("examples/weather/pom.xml", TACHYON_SERVER_VERSION_PROPERTY));

        assertThat(extractedVersions.values()).as("versions extracted from %s", extractedVersions).allMatch(
                CURRENT_VERSION::equals);
    }

    @Test
    void noneOfTheUpdatedFilesStillReferenceThePreviousBetaVersion() {
        final List<String> relativePaths = List.of(
                "README.md",
                "docs/quickstart.md",
                "docs/migrate-from-kotlin-mcp-to-tachyon.md",
                "tachyon-server-kotlin/README.md",
                "examples/echo-kotlin/pom.xml",
                "examples/weather/pom.xml");

        assertAll(relativePaths.stream()
                .<Executable>map(relativePath -> () -> assertThat(readRepoFile(relativePath))
                        .as("content of %s", relativePath)
                        .doesNotContain(PREVIOUS_VERSION))
                .collect(Collectors.toList()));
    }

    private static String extractVersion(final String relativePath, final Pattern pattern) {
        final String content = readRepoFile(relativePath);
        final Matcher matcher = pattern.matcher(content);
        assertThat(matcher.find())
                .as("expected pattern '%s' to match in %s", pattern.pattern(), relativePath)
                .isTrue();
        return matcher.group(1);
    }

    private static String readRepoFile(final String relativePath) {
        try {
            return Files.readString(repoRoot().resolve(relativePath));
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read " + relativePath, e);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve(".git"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repository root (no .git directory found)");
    }
}