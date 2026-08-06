/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.extensions.skills;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared storage and scanning for the default {@link SkillsRegistry} implementations. */
abstract class BaseSkillsRegistry implements SkillsRegistry {

    private static final Logger logger = LoggerFactory.getLogger(BaseSkillsRegistry.class);

    private final Map<String, SkillsRegistry.Skill> skillsByPath = new LinkedHashMap<>();
    private final Map<String, byte[]> bytesByUri = new HashMap<>();

    @Override
    public final List<SkillsRegistry.Skill> skills() {
        return List.copyOf(skillsByPath.values());
    }

    @Override
    public final @Nullable byte[] readFile(String fileUri) {
        return bytesByUri.get(fileUri);
    }

    /** Scans a directory of skills: every subdirectory containing a {@code SKILL.md} becomes a skill. */
    protected final void addSkillDir(Path root) {
        try (var children = Files.list(root)) {
            children.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(child -> child.getFileName().toString()))
                    .forEach(child -> addPath(child, child.getFileName().toString(), false));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Scans a single skill directory; rethrows {@link IllegalArgumentException} on invalid skills. */
    protected final void addSkill(Path skillDir, String skillPath) {
        addPath(skillDir, skillPath, true);
    }

    /** Registers a skill from pre-read files (relative path → bytes); skips invalid skills unless strict. */
    protected final void addFiles(String skillPath, Map<String, byte[]> files, boolean strict) {
        try {
            var skill = SkillsScanner.buildSkill(skillPath, files);
            skillsByPath.put(skill.skillPath(), skill);
            for (var file : skill.files()) {
                bytesByUri.put(file.uri(), files.get(file.relativePath()));
            }
        } catch (IllegalArgumentException e) {
            if (strict) {
                throw e;
            }
            logger.warn("Skipping skill directory '{}': {}", skillPath, e.getMessage());
        }
    }

    private void addPath(Path skillDir, String skillPath, boolean strict) {
        try (var walk = Files.walk(skillDir)) {
            var files = new HashMap<String, byte[]>();
            walk.filter(Files::isRegularFile).forEach(file -> {
                var relative = skillDir.relativize(file).toString().replace('\\', '/');
                try {
                    files.put(relative, Files.readAllBytes(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            addFiles(skillPath, files, strict);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
