/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.extensions.skills;

import java.nio.file.Path;

/**
 * Default {@link SkillsRegistry} serving skills from the filesystem.
 *
 * <p>Use {@link SkillsExtension.Builder#addSkillDir(Path)} or
 * {@link SkillsExtension.Builder#addSkill(Path, String)} instead of constructing directly.
 */
public final class PathSkillsRegistry extends BaseSkillsRegistry {

    /**
     * Creates a registry from a directory of skills: every subdirectory containing a
     * {@code SKILL.md} becomes a skill named after its directory.
     *
     * @param skillsRoot the directory containing the skill directories
     */
    public PathSkillsRegistry(Path skillsRoot) {
        addSkillDir(skillsRoot);
    }

    /**
     * Creates a registry for a single skill directory.
     *
     * @param skillDir the skill directory
     * @param skillPath the skill path; its final segment must equal the frontmatter {@code name}
     */
    public PathSkillsRegistry(Path skillDir, String skillPath) {
        addSkill(skillDir, skillPath);
    }
}
