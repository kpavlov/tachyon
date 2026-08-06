/* Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors. */
package dev.tachyonmcp.extensions.skills;

import dev.tachyonmcp.api.json.JsonObject;
import dev.tachyonmcp.api.runtime.InteractionContext;
import dev.tachyonmcp.api.server.domain.BlobResourceContents;
import dev.tachyonmcp.api.server.domain.ResourceContents;
import dev.tachyonmcp.api.server.domain.TextResourceContents;
import dev.tachyonmcp.api.server.extensions.ExtensionContext;
import dev.tachyonmcp.api.server.extensions.ExtensionSettings;
import dev.tachyonmcp.api.server.extensions.ServerExtension;
import dev.tachyonmcp.api.server.features.resources.ResourceDescriptor;
import dev.tachyonmcp.core.server.domain.ServerErrors;
import dev.tachyonmcp.core.server.features.resources.MimeTypes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * MCP <a href="https://github.com/modelcontextprotocol/modelcontextprotocol/pull/2640">SEP-2640</a>
 * skills extension: serves Agent Skills as {@code skill://} resources and answers
 * {@code skills/list}, {@code skills/get}, and {@code resources/directory/read}.
 *
 * <pre>{@code
 * TachyonServer.builder()
 *         .extension(SkillsExtension.builder()
 *                 .addSkillDir(Path.of("skills"))
 *                 .addClasspathSkillDir("bundled-skills")
 *                 .build())
 *         .build();
 * }</pre>
 */
public final class SkillsExtension implements ServerExtension {

    /** Extension identifier per SEP-2640. */
    public static final String ID = "io.modelcontextprotocol/skills";

    private static final String SKILL_SCHEME = "skill://";
    private static final String SKILL_MD = "/SKILL.md";
    private static final String DIRECTORY_MIME = "inode/directory";

    private final SkillsRegistry registry;
    private final Map<String, SkillsRegistry.Skill> skillsByPath = new LinkedHashMap<>();
    private final Set<String> fileUris;

    private SkillsExtension(List<SkillsRegistry> registries) {
        this.registry = new CompositeSkillsRegistry(registries);
        var uris = new HashSet<String>();
        for (var skill : registry.skills()) {
            skillsByPath.put(skill.skillPath(), skill);
            for (var file : skill.files()) {
                uris.add(file.uri());
            }
        }
        this.fileUris = Set.copyOf(uris);
    }

    /** Creates a new {@link SkillsExtension} builder. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String extensionId() {
        return ID;
    }

    @Override
    public ExtensionSettings serverSettings() {
        return ExtensionSettings.of(Map.of("directoryRead", true));
    }

    /**
     * Registers every skill file as an extension-owned resource and the {@code skills/list},
     * {@code skills/get}, and {@code resources/directory/read} methods.
     */
    @Override
    public void bootstrap(ExtensionContext server) {
        for (var skill : skillsByPath.values()) {
            for (var file : skill.files()) {
                var descriptor = ResourceDescriptor.builder()
                        .name(skill.skillPath() + "/" + file.relativePath())
                        .uri(file.uri())
                        .mimeType(file.mimeType())
                        .extensionId(ID);
                if (file.relativePath().equals("SKILL.md")) {
                    descriptor.description(String.valueOf(skill.frontmatter().get("description")));
                }
                server.resources().register(descriptor.build(), (context, request) -> contents(file));
            }
        }
        server.registerHandler("skills/list", this::listSkills);
        server.registerHandler("skills/get", this::getSkill);
        server.registerHandler("resources/directory/read", this::readDirectory);
    }

    private ResourceContents contents(SkillsRegistry.SkillFile file) {
        var bytes = registry.readFile(file.uri());
        if (bytes == null) {
            throw new IllegalStateException("Skill file is no longer available: " + file.uri());
        }
        return MimeTypes.isText(file.mimeType())
                ? TextResourceContents.of(file.uri(), new String(bytes, StandardCharsets.UTF_8), file.mimeType(), null)
                : BlobResourceContents.of(file.uri(), bytes, file.mimeType(), null);
    }

    private Object listSkills(InteractionContext interaction, @Nullable JsonObject params) {
        if (params != null && params.has("cursor")) {
            // ponytail: no pagination — catalogs are bounded; page with a server-side cursor if they grow
            return ServerErrors.invalidParams("skills/list pagination is not supported");
        }
        return Map.of(
                "skills",
                skillsByPath.values().stream().map(SkillsExtension::skillEntry).toList());
    }

    private Object getSkill(InteractionContext interaction, @Nullable JsonObject params) {
        var uri = params != null ? params.stringOpt("uri").orElse(null) : null;
        if (uri == null) {
            return ServerErrors.invalidParams("Missing 'uri' parameter");
        }
        var skill = findSkill(uri);
        if (skill == null) {
            return ServerErrors.invalidParams("Unknown skill: " + uri);
        }
        return Map.of("skill", skillEntry(skill));
    }

    private Object readDirectory(InteractionContext interaction, @Nullable JsonObject params) {
        var uri = params != null ? params.stringOpt("uri").orElse(null) : null;
        if (uri == null) {
            return ServerErrors.invalidParams("Missing 'uri' parameter");
        }
        if (!uri.startsWith(SKILL_SCHEME) || fileUris.contains(uri)) {
            return ServerErrors.invalidParams("Unknown skill directory: " + uri);
        }
        var children = directoryChildren(uri.substring(SKILL_SCHEME.length()));
        if (children == null) {
            return ServerErrors.invalidParams("Unknown skill directory: " + uri);
        }
        return Map.of("resources", toResourceEntries(uri, children));
    }

    /** Returns the immediate children of {@code dirPath}, or {@code null} when it names no known directory. */
    private @Nullable TreeMap<String, String> directoryChildren(String dirPath) {
        var dirPrefix = dirPath.isEmpty() ? "" : dirPath + "/";
        var children = new TreeMap<String, String>();
        var found = false;
        for (var skill : skillsByPath.values()) {
            var skillPath = skill.skillPath();
            if (skillPath.equals(dirPath)) {
                found |= collectChildren(skill, "", children);
            } else if (dirPath.startsWith(skillPath + "/")) {
                found |= collectChildren(skill, dirPath.substring(skillPath.length() + 1) + "/", children);
            } else if (skillPath.startsWith(dirPrefix)
                    && !skillPath.substring(dirPrefix.length()).contains("/")) {
                found = true;
                children.putIfAbsent(skillPath.substring(dirPrefix.length()), DIRECTORY_MIME);
            }
        }
        return found ? children : null;
    }

    /** Adds every file of {@code skill} under {@code innerPrefix} as a child; returns whether any matched. */
    private static boolean collectChildren(
            SkillsRegistry.Skill skill, String innerPrefix, Map<String, String> children) {
        var matched = false;
        for (var file : skill.files()) {
            if (!file.relativePath().startsWith(innerPrefix)) {
                continue;
            }
            matched = true;
            var rest = file.relativePath().substring(innerPrefix.length());
            var index = rest.indexOf('/');
            children.putIfAbsent(
                    index >= 0 ? rest.substring(0, index) : rest, index >= 0 ? DIRECTORY_MIME : file.mimeType());
        }
        return matched;
    }

    private static List<Map<String, Object>> toResourceEntries(String parentUri, Map<String, String> children) {
        return children.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> child = new LinkedHashMap<>();
                    child.put("uri", parentUri + "/" + entry.getKey());
                    child.put("name", entry.getKey());
                    child.put("mimeType", entry.getValue());
                    return child;
                })
                .toList();
    }

    private static Map<String, Object> skillEntry(SkillsRegistry.Skill skill) {
        var resources = new ArrayList<Map<String, Object>>(skill.files().size());
        for (var file : skill.files()) {
            var resource = new LinkedHashMap<String, Object>();
            resource.put("uri", file.uri());
            resource.put("digest", file.digest());
            resources.add(resource);
        }
        var entry = new LinkedHashMap<String, Object>();
        entry.put("uri", skill.skillUri());
        entry.put("frontmatter", skill.frontmatter());
        entry.put("resources", resources);
        return entry;
    }

    private SkillsRegistry.@Nullable Skill findSkill(String uri) {
        var skillPath = skillPathOf(uri);
        return skillPath != null ? skillsByPath.get(skillPath) : null;
    }

    private static @Nullable String skillPathOf(String uri) {
        if (!uri.startsWith(SKILL_SCHEME) || !uri.endsWith(SKILL_MD)) {
            return null;
        }
        var path = uri.substring(SKILL_SCHEME.length(), uri.length() - SKILL_MD.length());
        return path.isEmpty() ? null : path;
    }

    /** Configures and builds a {@link SkillsExtension}. */
    public static final class Builder {

        private final List<SkillsRegistry> registries = new ArrayList<>();

        /**
         * Adds every skill directory under {@code skillsRoot}: each subdirectory containing a
         * {@code SKILL.md} becomes a skill named after its directory.
         *
         * @param skillsRoot the directory containing the skill directories
         * @return this builder
         */
        public Builder addSkillDir(Path skillsRoot) {
            return registry(new PathSkillsRegistry(skillsRoot));
        }

        /**
         * Adds a single skill from the directory {@code skillDir}, named after its directory.
         *
         * @param skillDir the skill directory
         * @return this builder
         */
        public Builder addSkill(Path skillDir) {
            return addSkill(skillDir, skillDir.getFileName().toString());
        }

        /**
         * Adds a single skill from the directory {@code skillDir} under the given skill path.
         *
         * @param skillDir the skill directory
         * @param skillPath the skill path; its final segment must equal the frontmatter {@code name}
         * @return this builder
         */
        public Builder addSkill(Path skillDir, String skillPath) {
            return registry(new PathSkillsRegistry(skillDir, skillPath));
        }

        /**
         * Adds every classpath skill directory under {@code skillsResource}.
         *
         * @param skillsResource the classpath directory containing the skill directories
         * @return this builder
         */
        public Builder addClasspathSkillDir(String skillsResource) {
            return registry(new ClasspathSkillsRegistry(skillsResource));
        }

        /**
         * Adds a single classpath skill directory, named after its last path segment.
         *
         * @param skillResource the classpath path of the skill directory
         * @return this builder
         */
        public Builder addClasspathSkill(String skillResource) {
            return addClasspathSkill(skillResource, SkillsScanner.lastSegment(skillResource));
        }

        /**
         * Adds a single classpath skill directory under the given skill path.
         *
         * @param skillResource the classpath path of the skill directory
         * @param skillPath the skill path; its final segment must equal the frontmatter {@code name}
         * @return this builder
         */
        public Builder addClasspathSkill(String skillResource, String skillPath) {
            return registry(new ClasspathSkillsRegistry(skillResource, skillPath));
        }

        /** Adds a custom skill registry. */
        public Builder registry(SkillsRegistry registry) {
            registries.add(registry);
            return this;
        }

        /** Builds the extension. */
        public SkillsExtension build() {
            return new SkillsExtension(registries);
        }
    }
}
