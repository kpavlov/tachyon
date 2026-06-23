/*
 * Copyright (c) 2026 Konstantin Pavlov.
 */

package dev.tachyonmcp.server.features.tasks;

import dev.tachyonmcp.server.McpResourceType;
import dev.tachyonmcp.server.domain.Icon;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface TaskDescriptor extends McpResourceType {

    String name();

    @Nullable
    String description();

    @Nullable
    String title();

    @Nullable
    List<Icon> icons();

    static TaskDescriptor of(String name, @Nullable String description) {
        return new DefaultTaskDescriptor(name, description, null, null);
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    final class Builder {
        private final String name;
        private String description;
        private String title;
        private List<Icon> icons;

        Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder icons(List<Icon> icons) {
            this.icons = icons;
            return this;
        }

        public TaskDescriptor build() {
            return new DefaultTaskDescriptor(name, description, title, icons);
        }
    }
}
