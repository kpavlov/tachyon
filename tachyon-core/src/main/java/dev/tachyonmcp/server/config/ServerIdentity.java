/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.server.config;

import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Server identity metadata sent to the client during initialization.
 *
 * @param name         server name (required)
 * @param version      server version (required)
 * @param description  optional human-readable description
 * @param title        optional display title
 * @param websiteUrl   optional URL to the server's website
 * @param instructions optional instructions for how to use the server
 * @param icons        optional list of icon entries
 */
public record ServerIdentity(
        String name,
        String version,
        @Nullable String description,
        @Nullable String title,
        @Nullable String websiteUrl,
        @Nullable String instructions,
        @Nullable List<Icon> icons) {

    public static final ServerIdentity DEFAULT = new ServerIdentity("tachyon-mcp", "0.1", null, null, null, null, null);

    public ServerIdentity {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        if (icons != null) {
            icons = List.copyOf(icons);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ServerIdentity}. */
    public static final class Builder {
        private String name = "tachyon-mcp";
        private String version = "0.1";
        private @Nullable String description;
        private @Nullable String title;
        private @Nullable String websiteUrl;
        private @Nullable String instructions;
        private @Nullable List<Icon> icons;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        public Builder websiteUrl(@Nullable String websiteUrl) {
            this.websiteUrl = websiteUrl;
            return this;
        }

        public Builder instructions(@Nullable String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder icons(@Nullable List<Icon> icons) {
            this.icons = icons;
            return this;
        }

        public Builder icons(Icon... icons) {
            this.icons = List.of(icons);
            return this;
        }

        public ServerIdentity build() {
            return new ServerIdentity(name, version, description, title, websiteUrl, instructions, icons);
        }
    }
}
